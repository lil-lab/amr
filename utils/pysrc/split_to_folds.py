import random
import sys


samples = open(sys.argv[1]).read().strip().split('\n\n')
random.shuffle(samples)
outs = [open('fold'+ str(num) + '.ccg','w') for num in range(10)]
i = 0
for sample in samples:
    outs[i % 10].write(sample + '\n\n')
    i += 1
for out in outs: out.close()