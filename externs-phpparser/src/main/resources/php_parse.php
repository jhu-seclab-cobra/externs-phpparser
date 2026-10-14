<?php

/*
 * Derived from bin/php-parse of nikic/php-parser. It runs against a php-parser PHAR given as the
 * first argument, parses exactly one file, and encodes the JSON dump with invalid UTF-8 replaced
 * by U+FFFD, exiting non-zero when the encoding fails.
 *
 * Copyright (c) 2011, Nikita Popov
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

// The alias the PHAR's own stub maps, so its autoloader resolves whatever the PHAR file is named.
const PARSER_PHAR_ALIAS = 'php-parser.phar';

if (count($argv) < 2 || !is_file($argv[1])) {
    showHelp("Must specify the php-parser PHAR as the first argument.");
}
Phar::loadPhar($argv[1], PARSER_PHAR_ALIAS);
require 'phar://' . PARSER_PHAR_ALIAS . '/vendor/autoload.php';

ini_set('xdebug.max_nesting_level', 3000);

// Disable Xdebug var_dump() output truncation
ini_set('xdebug.var_display_max_children', -1);
ini_set('xdebug.var_display_max_data', -1);
ini_set('xdebug.var_display_max_depth', -1);

list($operations, $file, $attributes) = parseArgs(array_slice($argv, 2));

/* Dump nodes by default */
if (empty($operations)) {
    $operations[] = 'dump';
}

$parser = (new PhpParser\ParserFactory())->createForVersion($attributes['version']);
$dumper = new PhpParser\NodeDumper([
    'dumpComments' => true,
    'dumpPositions' => $attributes['with-positions'],
]);
$prettyPrinter = new PhpParser\PrettyPrinter\Standard;

$traverser = new PhpParser\NodeTraverser();
$traverser->addVisitor(new PhpParser\NodeVisitor\NameResolver);

if (!file_exists($file)) {
    fwrite(STDERR, "File $file does not exist.\n");
    exit(1);
}

$code = file_get_contents($file);
fwrite(STDERR, "====> File $file:\n");

if ($attributes['with-recovery']) {
    $errorHandler = new PhpParser\ErrorHandler\Collecting;
    $stmts = $parser->parse($code, $errorHandler);
    foreach ($errorHandler->getErrors() as $error) {
        $message = formatErrorMessage($error, $code, $attributes['with-column-info']);
        fwrite(STDERR, $message . "\n");
    }
    if (null === $stmts) {
        exit(0);
    }
} else {
    try {
        $stmts = $parser->parse($code);
    } catch (PhpParser\Error $error) {
        $message = formatErrorMessage($error, $code, $attributes['with-column-info']);
        fwrite(STDERR, $message . "\n");
        exit(1);
    }
}

foreach ($operations as $operation) {
    if ('dump' === $operation) {
        fwrite(STDERR, "==> Node dump:\n");
        echo $dumper->dump($stmts, $code), "\n";
    } elseif ('pretty-print' === $operation) {
        fwrite(STDERR, "==> Pretty print:\n");
        echo $prettyPrinter->prettyPrintFile($stmts), "\n";
    } elseif ('json-dump' === $operation) {
        fwrite(STDERR, "==> JSON dump:\n");
        echo encodeJson($stmts), "\n";
    } elseif ('var-dump' === $operation) {
        fwrite(STDERR, "==> var_dump():\n");
        var_dump($stmts);
    } elseif ('resolve-names' === $operation) {
        fwrite(STDERR, "==> Resolved names.\n");
        $stmts = $traverser->traverse($stmts);
    }
}

// JSON carries only UTF-8 text: each invalid byte sequence becomes U+FFFD, and a failure ends the run.
function encodeJson(array $stmts) {
    try {
        return json_encode($stmts, JSON_PRETTY_PRINT | JSON_INVALID_UTF8_SUBSTITUTE | JSON_THROW_ON_ERROR);
    } catch (JsonException $exception) {
        fwrite(STDERR, "JSON encoding failed: " . $exception->getMessage() . "\n");
        exit(1);
    }
}

function formatErrorMessage(PhpParser\Error $e, $code, $withColumnInfo) {
    if ($withColumnInfo && $e->hasColumnInfo()) {
        return $e->getMessageWithColumnInfo($code);
    } else {
        return $e->getMessage();
    }
}

function showHelp($error = '') {
    if ($error) {
        fwrite(STDERR, $error . "\n\n");
    }
    fwrite($error ? STDERR : STDOUT, <<<'OUTPUT'
Usage: php php_parse.php php-parser.phar [operations] file.php
Turn PHP source code into an abstract syntax tree.

Operations is a list of the following options (--dump by default):

    -d, --dump              Dump nodes using NodeDumper
    -p, --pretty-print      Pretty print file using PrettyPrinter\Standard
    -j, --json-dump         Print json_encode() result, invalid UTF-8 replaced by U+FFFD
        --var-dump          var_dump() nodes (for exact structure)
    -N, --resolve-names     Resolve names using NodeVisitor\NameResolver
    -c, --with-column-info  Show column-numbers for errors (if available)
    -P, --with-positions    Show positions in node dumps
    -r, --with-recovery     Use parsing with error recovery
        --version=VERSION   Target specific PHP version (default: newest)
    -h, --help              Display this page

Example:
    php php_parse.php php-parser.phar -d -p -N -d file.php

    Dumps nodes, pretty prints them, then resolves names and dumps them again.


OUTPUT
    );
    exit($error ? 1 : 0);
}

function parseArgs($args) {
    $operations = [];
    $files = [];
    $attributes = [
        'with-column-info' => false,
        'with-positions' => false,
        'with-recovery' => false,
        'version' => PhpParser\PhpVersion::getNewestSupported(),
    ];

    $parseOptions = true;
    foreach ($args as $arg) {
        if (!$parseOptions) {
            $files[] = $arg;
            continue;
        }

        switch ($arg) {
            case '--dump':
            case '-d':
                $operations[] = 'dump';
                break;
            case '--pretty-print':
            case '-p':
                $operations[] = 'pretty-print';
                break;
            case '--json-dump':
            case '-j':
                $operations[] = 'json-dump';
                break;
            case '--var-dump':
                $operations[] = 'var-dump';
                break;
            case '--resolve-names':
            case '-N':
                $operations[] = 'resolve-names';
                break;
            case '--with-column-info':
            case '-c':
                $attributes['with-column-info'] = true;
                break;
            case '--with-positions':
            case '-P':
                $attributes['with-positions'] = true;
                break;
            case '--with-recovery':
            case '-r':
                $attributes['with-recovery'] = true;
                break;
            case '--help':
            case '-h':
                showHelp();
                break;
            case '--':
                $parseOptions = false;
                break;
            default:
                if (preg_match('/^--version=(.*)$/', $arg, $matches)) {
                    $attributes['version'] = PhpParser\PhpVersion::fromString($matches[1]);
                } elseif ($arg[0] === '-' && \strlen($arg) > 1) {
                    showHelp("Invalid operation $arg.");
                } else {
                    $files[] = $arg;
                }
        }
    }

    if (count($files) !== 1) {
        showHelp("Must specify exactly one file.");
    }

    return [$operations, $files[0], $attributes];
}
