#!/usr/bin/perl

# This takes two files (presumably they are the same length
# and for any given line, that line is blank in one file and
# contains text in the other), escapes &<>, adds a root node, 
# and merges them into a new file.
#
# perl merge_file.pl sample.parse sample.markup
#
# Courtney Napoles, cdnapoles@gmail.com
# 2012-06-29

use HTML::Entities;

if (@ARGV != 2) {
    die "perl merge_file.pl PARSE_FILE MARKUP_FILE\n";
}

$parsefile = $ARGV[0];
$markupfile = $ARGV[1];

if ($parsefile =~ /\.gz$/) {
    open(PARSES, "gunzip -c $parsefile |") or die $!;
} else {
    open(PARSES, $parsefile) or die $!;
}

if ($markupfile =~ /\.gz$/) {
    open(MARKUP, "gunzip -c $markupfile |") or die $!;
} else {
    open(MARKUP, $markupfile) or die $!;
}

$parsefile =~/([^\/]+)\.[a-zA-Z]+$/;
$filename = $1;

# assuming that the input is not proper XML, we need to 
# add a root
print "<FILE id=\"$filename\">\n";
foreach $parse_line (<PARSES>) {
    $markup_line = <MARKUP>;
    chomp($parse_line);
    chomp($markup_line);
    if ($parse_line =~ /^$/) {
	# escape & if present and assume no angle brackets 
	# in the markup (besides the expected ones)
        $markup_line =~ s/(&)(?![a-z]+;)/&amp;/g;
        print $markup_line."\n";
    } else {
	# escape illegal xml characters <>&
        $parse_line = encode_entities($parse_line,"<>&");
        print $parse_line."\n";
    }
}

print "</FILE>\n";

close(PARSES);
close(MARKUP);
