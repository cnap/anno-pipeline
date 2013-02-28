#!/bin/bash

if [ $# -eq 0 ]
then
    echo "./parse.sh file/to/parse [destination]"
    exit
fi

if [ $# -eq 1 ]
then
    java -XX:+UseConcMarkSweepGC -Dfile.encoding=UTF-8 -Xmx16g -ss10m -cp ~/annotation/lib/umd-parser.jar edu.purdue.ece.speech.LAPCFG.PurdueParser -gr ~/annotation/lib/wsj-6.pml -input $1 -jobs 8 
else 
    java -XX:+UseConcMarkSweepGC -Dfile.encoding=UTF-8 -Xmx16g -ss10m -cp ~/annotation/lib/umd-parser.jar edu.purdue.ece.speech.LAPCFG.PurdueParser -gr ~/annotation/lib/wsj-6.pml -input $1 -jobs 8 -output $2
fi