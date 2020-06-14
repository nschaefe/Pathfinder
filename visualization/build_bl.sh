blName="./blacklist.json"
cat ./blacklists/* > $blName
if [ -s "$blName" ] 
then
	sed -i  '1s/^/[/;$!s/$/,/;$s/$/]/' $blName
else
	echo "[]" > $blName
fi
mv $blName ./src/$blName
