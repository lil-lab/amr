# this takes a Giza translation probability 
# file and translates it into a const-word
# translation probability
import sys
source_dict = open(sys.argv[1],'r')#raw_input('where is the source?\n'),'r')
target_dict = open(sys.argv[2],'r')#raw_input('where is the target?\n'),'r')
trans_probs = open(sys.argv[3],'r')#raw_input('where are the translation probs?\n'),'r')
out_file = open(sys.argv[4],'w')#raw_input('where should the output go?\n'),'w')

c_from_id = {}
c_from_id['0'] = 'null'
for l in source_dict:
    id = l.split()[0]
    const = l.split()[1]
    c_from_id[id] = const
    
w_from_id = {}
w_from_id['0'] = 'null'
for l in target_dict:
    id = l.split()[0]
    word = l.split()[1]
    w_from_id[id] = word


# c-w 
#for l in trans_probs:
#    c_id = l.split()[0]
#    w_id = l.split()[1]
#    prob = l.split()[2]
#    if c_from_id.has_key(c_id) and w_from_id.has_key(w_id):
#        print >> out_file,c_from_id[c_id],' :: ',w_from_id[w_id],' :: ',prob
#    elif not c_from_id.has_key(c_id):
#        print "not got const for "+c_id
#    elif not w_from_id.has_key(w_id):
#        print "not got word for "+w_id
# w-c 
for l in trans_probs:
    c_id = l.split()[0]
    w_id = l.split()[1]
    prob = l.split()[2]
    print >> out_file,w_from_id[w_id],' :: ',c_from_id[c_id],' :: ',prob

