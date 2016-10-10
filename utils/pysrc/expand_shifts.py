import sys


'''
Script to take underspecified CCG unary type shifting rules (represented using categories and application rules) and generate fully specified versions according to the given under-specification mapping.
The script also outputs the initial features for all these rules. The feature for using each rule is initialized to -3.0.
This script is used to generate the .shifts file for ablation of under-specification of relations. The generated weights must be appended to the weight initialization file.
'''


# Read spec map
specmap = {}
for line in open(sys.argv[1]).readlines():
    specmap[line.strip().split('\t')[0]] = set(line.strip().split('\t')[1].split(','))

# Read shifts and output expanded file
shifts_out = open('amr.nospec.shifts', 'w')
weight_out = open('amr.nospec.weights', 'w')
for line in open(sys.argv[2]).readlines():
    line= line.strip()
    split = line.split('\t')
    if len(split) == 1:
        print >> shifts_out, line
    else:
        printed = False
        for key, value in specmap.items():
            if key in split[1]:
                for speced in value:
                    rule_name = split[0] + '_' + speced.lower()
                    print >> shifts_out, '%s\t%s' % (rule_name, split[1].replace(key, speced))
                    print >> weight_out, 'SHIFT#' + split[0] + '_' + speced.lower() + '=-3.0'
                printed = True
                break
        if not printed:
            print >> shifts_out, line
