#!/usr/bin/env python

import sys

if len(sys.argv) <= 3:
    print "Usage: %s %s %s %s" % (sys.argv[0], "<mt_aligments>", "<jamr_alignments>", "<output_aligments>")
    exit(1)

def parse_properties(line):
    if not line.startswith("#"):
        return {}
    properties = {}
    for kv in line[1:].strip().split("::"):
        index = kv.find(" ")
        if index < 0:
            index = len(kv)
        properties[kv[:index]] = kv[index:].strip()
    return properties

def get_alignments(filename):
    sentences = {}
    with open(filename) as f:
        sentence = {}
        for i, line in enumerate(f.readlines()):
            if len(line.strip()) == 0:
                if "id" in sentence and "alignments" in sentence:
                    sentences[sentence["id"]] = sentence
                    sentence = {}
                continue
            sentence.update(parse_properties(line))
    return sentences

def convert_node(node):
    indices = [int(i) for i in node.split(".")]
    return ".".join("%d" % (i-1) for i in indices)

def convert_alignment(mt_alignment):
    if len(mt_alignment) == 0:
        return mt_alignment
    pairs = [s.split("-") for s in mt_alignment.split(" ")]
    cleaned_pairs = sorted([(int(i),convert_node(node)) for i,node in pairs if not node.endswith("r")], key=lambda x:x[0])
    return " ".join("%d-%d|%s" % (i, i+1, node) for i, node in cleaned_pairs)

mt = get_alignments(sys.argv[1])
jamr = get_alignments(sys.argv[2])

print "Alignment counts for %s:" % sys.argv[2]
print "%d alignments in mt" % len(mt)
print "%d alignments in jamr" % len(jamr)

matching = 0
total = 0
for k,v in jamr.items():
    if k in mt:
        matching += 1
    total += 1

print "%d/%d alignments from jamr in mt" % (matching, total)

with open(sys.argv[2]) as in_f:
    with open(sys.argv[3], "w") as out_f:
        current_properties = {}
        for line in in_f.readlines():
            current_properties.update(parse_properties(line))
            out_f.write(line)
            if line.startswith("# ::alignments") and current_properties["id"] in mt:
                out_f.write("# ::mt_alignments %s\n" % convert_alignment(mt[current_properties["id"]]["alignments"]))
                out_f.write("# ::mt_original_alignments %s\n" % mt[current_properties["id"]]["alignments"])
