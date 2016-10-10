import sys
from optparse import OptionParser
import re

if __name__ == '__main__':
    parser = OptionParser(usage = 'usage: %prog -o output_file input_file')
    parser.add_option('-o', '--output', dest = 'output', help = 'Output file.')
    (options, args) = parser.parse_args()

    out = open(options.output,'w')
    for line in open(args[0]).readlines():
        if (line.startswith('# ::snt ')) and '-' in line:
            print >> sys.stderr, 'Stripping hyphens from: ' + line.strip()
            out.write(re.sub('\s+', ' ', line.replace('-', ' ').strip()))
            out.write('\n')
        else:
            out.write(line)
