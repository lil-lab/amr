from pattern.en import conjugate, lemma, lexeme, pluralize, singularize
import pattern.en
import sys



for word in sys.stdin:
    word = word.strip()
    related_forms = []
    related_forms.append(pluralize(word))
    related_forms.append(singularize(word))
    related_forms.append(lemma(word))
    related_forms += lexeme(word)
    print '%s\t%s' % (word, '\t'.join(set(related_forms)))