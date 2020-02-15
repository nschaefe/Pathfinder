#!/bin/sh

files=$(find ./reports -type f)
for i in $files
do
  cat $i | uniq > tmp4242 
  rm $i
  mv tmp $i
done
rm tmp4242
