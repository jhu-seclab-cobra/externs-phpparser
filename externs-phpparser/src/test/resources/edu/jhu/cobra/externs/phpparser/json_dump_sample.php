<?php
// leading comment
namespace App;
/** doc */
function f(int $a = 1, ...$rest): ?string { return null; }
$x = [1 => 1.5, 'k' => "a/b\n\u{e9}", 2.0, -0.0, 1e20];
$o = new \stdClass();
