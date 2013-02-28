#!/bin/bash

# This annotates a Gigaword document using Splitta 1.03,
# the Stanford PTB Tokenizer, the UMD parser, and a modified
# Stanford CoreNLP pipeline (including dependencies, coref
# chains, and NER).
#
# ./pipeline FILE DIR [OPTIONS]
#
# where FILE is the input file and DIR is the directory where
# the intermediate files (necessary for the pipeline) and 
# annotated file are saved.
#
# Options are
#     --tok    t|f   : perform tokenization (default: t)
#     --split  t|f   : perform sentence segmentation (default: t)
#     --sgml   t|f   : FILE is in SGML format (default: t)
#     --parsed t|f   : input is parsed (default: f)
#     --doc    t|f   : text has a document structure (default: t)
#                      if f, then coref resolution is not done
#
# --sgml f assumes that there is one doc in FILE. If there is 
# more than one doc in FILE and FILE is not in SGML format, set
# --doc f
#
# The final output will be in DIR/FILE.annotated.xml
#
# Courtney Napoles, cdnapoles@gmail.com
# 2012-06-29, ed. 2013-02-28

function usage {
    echo "Usage: ./pipeline FILE DIR [OPTIONS] 

FILE is the input file and DIR is the directory where intermediate
files and the annotated file are saved. Options are
    --tok    t|f   : perform tokenization (default: t)
    --split  t|f   : perform sentence segmentation (default: t)
    --sgml   t|f   : FILE is in SGML format (default: t)
    --parsed t|f   : input is parsed (default: f)
    --doc    t|f   : text has a document structure (default: t)
                     if f, then coreference resolution is not done

The final annotation will be saved in DIR/FILE.annotated.xml"
    exit
}

if [ $# -lt 2 ]; then
    usage
fi

origfile=$1
shift
wrkdir=$1
shift
f=`basename $origfile`
f=${f%.*}

if [ ! -f $origfile ]; then
    echo "Cannot find $origfile"
    usage
fi

export PYTHONPATH=$PYTHONPATH:lib/splitta.1.03
export LC_ALL=en_US.UTF-8
AGIGA_HOME=/home/hltcoe/cnapoles/annotation

tok=true
split=true
sgml=true
parsed=false
doc=true

while true; do
    case "$1" in
	(--tok) 
	    case "$2" in
		t) tok=true ; shift 2 ;;
		f) tok=false ; shift 2 ;;
		*) usage ;;
		esac ;;
	--split)
	    case "$2" in
		t) split=true ; shift 2 ;;
		f) split=false ; shift 2 ;;
		*) usage ;;
		esac ;;
	--sgml)
	    case "$2" in
		t) sgml=true ; shift 2 ;;
		f) sgml=false ; shift 2 ;;
		*) usage ;;
		esac ;;
	--parsed)
	    case "$2" in
		t) parsed=true ; shift 2 ;;
		f) parsed=false ; shift 2 ;;
		*) usage ;;
		esac ;;
	--doc)
	    case "$2" in
		t) doc=true ; shift 2 ;;
		f) doc=false ; shift 2 ;;
		*) usage ;;
		esac ;;
	--) shift ; break ;;
	-h|--help) usage ;;
	--*) echo "$1: illegal option"; usage ;;
	*) break ;;
    esac
done

if [ ! -d "$wrkdir" ]; then
    mkdir "$wrkdir"
fi

# 1. Concatenate lines of text and split into sentences
if $split; then
    cmd="$AGIGA_HOME/scripts/scat $origfile | \
	python $AGIGA_HOME/scripts/split_sentences.py > $wrkdir/$f.split"
    echo $cmd
    eval $cmd
fi

# 2. Tokenize sentences
if $tok; then
    cmd="java -mx100m -cp $AGIGA_HOME/lib/stanford-corenlp-2012-05-22.jar \
	edu.stanford.nlp.process.PTBTokenizer -options ptb3Escaping \
        -preserveLines $wrkdir/$f.split > $wrkdir/$f.tok"
    echo $cmd
    eval $cmd
fi

# 3. Separate SGML markup from tokenized lines (necessary because the 
#    parser does not skip markup).
if $sgml; then
    cmd="cat $wrkdir/$f.tok | python $AGIGA_HOME/scripts/separate_lines.py \
	$wrkdir/$f.markup > $wrkdir/$f.to_parse"
    echo $cmd
    eval $cmd
fi

# 3a. Replace non-breaking spaces introduced by the PTB tokenizer in 
#     lines containing SGML markup.
if $sgml && $tok; then
    perl -pi -e 's/\x{c2}\x{a0}/ /g' $wrkdir/$f.markup
fi

echo "Done preprocessing $f. Begin parsing..."

# 4. Parse
if ! $parsed; then
    cmd="java -Xmx16g -ss10m -cp $AGIGA_HOME/lib/umd-parser.jar \
	edu.purdue.ece.speech.LAPCFG.PurdueParser -gr $AGIGA_HOME/lib/wsj-6.pml \
        -input $wrkdir/$f.to_parse -output $wrkdir/$f.parse -jobs 8"
    echo $cmd
    eval $cmd
fi

# 5. Merge markup and parses into one file and make legal XML (by adding a root
#    node and escaping <>&.
if $sgml && ! $parsed; then
    cmd="perl $AGIGA_HOME/scripts/merge_file.pl $wrkdir/$f.parse $wrkdir/$f.markup \
	> $wrkdir/$f.merged"
    echo $cmd
    eval $cmd
fi

echo "Done parsing $f. Begin annotating..."

# 6. Annotate file with modified Stanford pipeline and convert to true XML
#    This assumes that the file will be structured as <FILE><DOC><TEXT>parsed 
#    lines</TEXT></DOC></FILE>. For more options see 
#    edu.jhu.annotation.GigawordAnnotator
flags=""

if ! $sgml; then
    flags="$flags --sgml f"
fi

if ! $doc; then
    if ! $sgml; then
	flags="$flags --sents t"
    else
	flags="$flags --coref f"
    fi
fi

cmd="java -Xmx16g -Dfile.encoding=UTF-8 -cp $AGIGA_HOME/bin:$AGIGA_HOME/lib/stanford-corenlp-2012-05-22.jar:$AGIGA_HOME/lib/my-xom.jar:$AGIGA_HOME/lib/stanford-corenlp-2012-05-22-models.jar:$AGIGA_HOME/lib/joda-time.jar \
    edu.jhu.annotation.GigawordAnnotator --in $wrkdir/$f.merged $flags > $wrkdir/$f.annotated.xml 2>> $wrkdir/$f.errors"
echo $cmd
eval $cmd

# This is a bad hack that you need if illegal UTF8 characters are present
# in the output. This line will delete those characters. You should not do 
# this without examining what characters will be deleted.
#cat $wrkdir/$f.intermed | iconv -t UTF8//IGNORE > $wrkdir/$f.xml

echo "Done. Final annotation: $wrkdir/$f.annotated.xml"
