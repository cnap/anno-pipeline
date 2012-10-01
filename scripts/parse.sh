#!/bin/bash

java -Dfile.encoding=UTF-8 -Xmx16g -ss10m -cp ~/annotation/lib/umd-parser.jar edu.purdue.ece.speech.LAPCFG.PurdueParser -gr ~/annotation/lib/wsj-6.pml -input $1 -jobs 8