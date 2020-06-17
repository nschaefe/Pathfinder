blName="./blacklist.json"
blDir="./blacklists"
if [ ! -d "$blDir" ];
then
	echo "No blacklists directory"
	exit 1
fi

cat $blDir/* > $blName
if [ -s "$blName" ] 
then
	sed -i  '1s/^/[/;$!s/$/,/;$s/$/]/' $blName
else
	echo "[]" > $blName
fi
mv $blName ./src/$blName
