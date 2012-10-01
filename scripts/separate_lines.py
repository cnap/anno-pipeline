#!/usr/bin/python

# This script separates SGML markup from sentences to be
# parsed. sample.markup contains all of the lines with SGML 
# markup and sentences with greater than 100 tokens. All
# other sentences are directed to stdout.
#
# cat sample.xml | python separate_lines.py sample.markup \
#     > sample.to_parse
#
# Courtney Napoles, cdnapoles@gmail.com
# 2012-06-29

import sys, os 
from subprocess import call


markup_path = sys.argv[1]
markupfile = open(markup_path,'wb')

for line in sys.stdin :
    if line.startswith('<') :
        markupfile.write(line.rstrip()+'\n')
        print ''
    elif len(line.split()) > 100 :
        markupfile.write(line.rstrip()+'\n')
        print ''
    else :
        print line.strip()
        markupfile.write('\n')

markupfile.close()
