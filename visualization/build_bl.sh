blName="./blacklist.json"
blDir="./blacklists"
if [ ! -d "$blDir" ];
then
	echo "No blacklists directory"
	exit 1
fi

> $blName
for f in $blDir/* ; do (cat "${f}"; echo) >> $blName; done
#cat $blDir/* > $blName
if [ -s "$blName" ] 
then
	sed -i  '1s/^/[/;$!s/$/,/;$s/$/]/' $blName
else
	echo "[]" > $blName
fi
mv $blName ./src/$blName
