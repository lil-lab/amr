
import sys
import os


# these can be set to cut out 
# rare words and constants
minWordCount=0
minConstCount=0



input_file = sys.argv[1]
print 'input file is ',input_file
word_dict_output = open(sys.argv[2],'w')
const_dict_output =  open(sys.argv[3],'w')
bitext_output =  open(sys.argv[4],'w')


# Need 3 files:
#	word vocab file with unique id and counts
#	const vocab file with unique id and counts
#	bitext file with:
#		no of occurences
#		word ids
#		const ids

charset =  'a b c d e f g h i j k l m n o p q r s t u v w x y z 0 1 2 3 4 5 6 7 8 9'.split()
word_id_no = 1	
word_dict = {}

const_id_no = 1
const_dict = {}

sent_pair_dict = {}
sent = None

for line in open(input_file,'r'):
	if line != '\n' and line[:2] != '//' and sent==None:
		sent = line
		words = sent.split()
		word_id_string = ''
		for word in words:
			if not word_dict.has_key(word):
				word_dict[word] = [word_id_no,1]
				word_id_no += 1
			else:
				word_dict[word][1]+=1
			word_id_string = word_id_string+str(word_dict[word][0])+' '
			
	elif line != '\n' and line[:2] != '//' and sent != None:
		consts = line.split()
		rep_id_string = ''
		for const in consts:
			const = const.strip().rstrip()
			if const!='e' and const.find('$')==-1 and const != 'lambda':
				if not const_dict.has_key(const):
					const_dict[const] = [const_id_no,1]
					const_id_no += 1
				else:
					const_dict[const][1] += 1
				rep_id_string = rep_id_string+str(const_dict[const][0])+' '
		word_id_string.rstrip()
		rep_id_string.rstrip()
		if not sent_pair_dict.has_key((word_id_string,rep_id_string)):
			sent_pair_dict[(word_id_string,rep_id_string)] = 1
		else:
			sent_pair_dict[(word_id_string,rep_id_string)] += 1
		sent = None
	elif line == '\n':
		sent = None

w_outs = []
usablew = []
for w in word_dict:
	w_outs.append((word_dict[w][0],w,word_dict[w][1]))
	
w_outs.sort()
for w_out in w_outs:
	if w_out[2] >= minWordCount: 
		print >> word_dict_output,w_out[0],' ',w_out[1],' ',w_out[2]
		usablew.append(w_out[0])

c_outs = []
usablec = []
for c in const_dict:
	c_outs.append((const_dict[c][0],c,const_dict[c][1]))
c_outs.sort()

for c_out in c_outs:
	if c_out[2] >minConstCount: 
		print >> const_dict_output,c_out[0],' ',c_out[1],' ',c_out[2]
		usablec.append(c_out[0])
# source == words
print "usable w is ",usablew
print "usable c is ",usablec

for sent_pair in sent_pair_dict:
	wout = []
	cout = []
	for w in sent_pair[0].split():
		if int(w) in usablew: 
			wout.append(w)
						

	for c in sent_pair[1].split():
		if int(c) in usablec: cout.append(c)
	if len(wout)==0 or len(cout)==0: continue
	print >> bitext_output,sent_pair_dict[sent_pair]
	for w in wout: print >> bitext_output,w,
	print >> bitext_output,""
	for c in cout: print >> bitext_output,c,
	print >> bitext_output,""
