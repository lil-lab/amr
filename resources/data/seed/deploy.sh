grep -h " :- " *.lamlex | gsed -e 's/S\[[a-z]\+\]/S/g' | sort | uniq  > ../../seed.lex
cat *.lamlex | gsed -e 's/S\[[a-z]\+\]/S/g' > ../seed.lamlex
