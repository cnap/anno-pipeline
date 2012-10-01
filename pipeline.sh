#!/bin/bash

# This annotates a Gigaword document using Splitta 1.03,
# the Stanford PTB Tokenizer, the UMD parser, and a modified
# Stanford CoreNLP pipeline (including dependencies, coref
# chains, and NER).
#
# ./pipeline inputfile working_directory
#
# Courtney Napoles, cdnapoles@gmail.com
# 2012-06-29


if [ $# -ne 2 ]
then
    echo "./pipeline.sh filepath working_dir"
    exit
fi


origfile=$1
wrkdir=$2
f=`basename $1`
f=${f%.*}

# 1. Concatenate lines of text and split into sentences
export PYTHONPATH=$PYTHONPATH:lib/splitta.1.03

cmd="./scripts/scat $origfile | python scripts/split_sentences.py \
	> $wrkdir/$f.split"
echo $cmd
eval $cmd

# 2. Tokenize sentences
cmd="java -mx100m -cp lib/stanford-corenlp-2012-05-22.jar \
	edu.stanford.nlp.process.PTBTokenizer -options ptb3Escaping \
        -preserveLines $wrkdir/$f.split > $wrkdir/$f.tok"
echo $cmd
eval $cmd

# 3. Separate SGML markup from tokenized lines
cmd="cat $wrkdir/$f.tok | python scripts/separate_lines.py $wrkdir/$f.markup \
        > $wrkdir/$f.to_parse"
echo $cmd
eval $cmd

echo "Done preprocessing $f. Begin parsing..."

# 4. Parse

cmd="java -Xmx16g -ss10m -cp lib/umd-parser.jar \
    edu.purdue.ece.speech.LAPCFG.PurdueParser -gr lib/wsj-6.pml \
    -input $wrkdir/$f.to_parse -output $wrkdir/$f.parse -jobs 8"
echo $cmd
eval $cmd

echo "Done parsing $f. Begin merging..."

# 5. Merge markup and parses
cmd="perl scripts/merge_file.pl $wrkdir/$f.parse $wrkdir/$f.markup \
	> $wrkdir/$f.merged"
echo $cmd
eval $cmd

echo "Done merging $f. Begin annotating..."

# 6. Annotate file with modified Stanford pipeline and convert to true XML
#    This assumes that the file will be structured as <FILE><DOC><TEXT>parsed 
#    lines</TEXT></DOC></FILE>. For more options see 
#    edu.jhu.annotation.GigawordAnnotator
cmd="java -Xmx16g -cp bin:lib/stanford-corenlp-2012-05-22.jar:lib/my-xom.jar:lib/stanford-corenlp-2012-05-22-models.jar:lib/joda-time.jar \
    edu.jhu.annotation.GigawordAnnotator --in $wrkdir/$f.merged > $wrkdir/$f.xml 2>> $wrkdir/$f.errors"

echo $cmd
eval $cmd

#cat $wrkdir/$f.intermed | iconv -t UTF8//IGNORE > $wrkdir/$f.xml

echo "Done. Final annotation: $wrkdir/$f.xml"