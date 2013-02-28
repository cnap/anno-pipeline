#!/usr/bin

# This script will take a parsed file in Gigaword SGML format and 
# run it through the modified Stanford Core NLP pipeline

java -Xmx16g -Dfile.encoding=UTF-8 -cp /home/hltcoe/cnapoles/annotation/bin:/home/hltcoe/cnapoles/annotation/lib/stanford-corenlp-2012-05-22.jar:/home/hltcoe/cnapoles/annotation/lib/my-xom.jar:/home/hltcoe/cnapoles/annotation/lib/stanford-corenlp-2012-05-22-models.jar:/home/hltcoe/cnapoles/annotation/lib/joda-time.jar \
    edu.jhu.annotation.GigawordAnnotator --in $@
