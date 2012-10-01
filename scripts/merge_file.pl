#!/usr/bin/perl

# This takes two files (presumably they are the same length
# and for any given line, that line is blank in one file and
# contains text in the other), escapes ampersands, and 
# merges them into a new file.
#
# perl merge_file.pl sample.parse sample.markup
#
# Courtney Napoles, cdnapoles@gmail.com
# 2012-06-29


if (@ARGV != 2) {
    die "perl merge_file.pl fileA fileB\n";
}

$fileA = $ARGV[0];
$fileB = $ARGV[1];

if ($fileA =~ /\.gz$/) {
    open(FILE_A, "gunzip -c $fileA |") or die $!;
} else {
    open(FILE_A, $fileA) or die $!;
}

if ($fileB =~ /\.gz$/) {
    open(FILE_B, "gunzip -c $fileB |") or die $!;
} else {
    open(FILE_B, $fileB) or die $!;
}

$fileA =~/([^\/]+)\.[a-zA-Z]+$/;
$filename = $1;

# assuming that the input is not proper XML, we need to 
# add a root
print "<FILE id=\"$filename\">\n";
foreach $line_a (<FILE_A>) {
    $line_b = <FILE_B>;
    chomp($line_a);
    chomp($line_b);
    if ($line_a =~ /^$/) {
        $line_b =~ s/(&)(?![a-z]+;)/&amp;/g;
        print $line_b."\n";
    } else {
        $line_a =~ s/(&)(?![a-z]+;)/&amp;/g;
        print $line_a."\n";
    }
}

print "</FILE>\n";

close(FILE_A);
close(FILE_B);
