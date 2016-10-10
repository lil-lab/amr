from optparse import OptionParser
import re
import json



def process_sample(sentence_str, amr_str):
    d = dict(map(lambda x: x if len(x) == 2 else (x[0], ''),
                 map(lambda x: x.split(' ',1),
                     filter(lambda x: x != '',
                        map(lambda x: x.strip(),
                            ' '.join(map(lambda x: x.lstrip('#').strip(), sentence_str.split('\n'))).split('::'))))))
    sentence = d['snt']
    del d['snt']
    return sentence, 'JSON' + json.dumps(d.items()), amr_str

if __name__ == '__main__':
    parser = OptionParser(usage = 'usage: %prog -o output_file input_file')
    parser.add_option('-o', '--output', dest = 'output', help = 'Output file.')
    (options, args) = parser.parse_args()

    out = open(options.output,'w')

    # Split the input file according to empty lines
    segments = filter(lambda x: len(x) != 0, open(args[0]).read().split('\n\n'))
    for seg in segments[1:]:
        sentence_str = '\n'.join(filter(lambda x: x.startswith('#'), seg.split('\n')))
        amr_str = '\n'.join(filter(lambda x: not x.startswith('#'), seg.split('\n')))
        sentence, props, amr = process_sample(sentence_str, amr_str)
        out.write(sentence)
        out.write('\n')
        out.write(props)
        out.write('\n')
        out.write(amr)
        out.write('\n\n')
    out.close()

