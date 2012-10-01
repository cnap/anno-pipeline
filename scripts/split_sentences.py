#!/usr/bin/python

# This script assumes that the input is not formatted with 
# one sentence per line. It skips suspected SGML markup (any
# line beginning with '<'). All other lines not separated 
# by an extra line break are concatenated and then split 
# with Splitta 1.03.
#
# cat sample.xml | python split_sentences.py
#
# Courtney Napoles, cdnapoles@gmail.com
# 2012-06-29

import sys, sbd, os

model_path = os.path.dirname(sbd.__file__)+'/model_svm/'
model = sbd.load_sbd_model(model_path,True)

def split(lines) :
    if len(lines) > 0 :
        test = sbd.get_text_data(' '.join(lines),tokenize=True)
        test.featurize(model,verbose=True)
        model.classify(test,verbose=True)
        for s in test.segment(use_preds=True, list_only=True) :
            print s


lines = []
for line in sys.stdin :
    if line.startswith('<') :
        split(lines)
        lines = []
        print line.rstrip()
    else :
        lines.append(line.strip())
if len(lines) > 0 : 
    split(lines)

