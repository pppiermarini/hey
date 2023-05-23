usages=($(df -am --output=pcent | grep [0-9] | tr -d %))
notify=false
usageCap=90

for element in "${usages[@]}"
do
	echo $element
	if ((${element} > $usageCap))
	then
		$notify = true
	fi
done

if ($notify == true)
then
	echo "Finding 5 Largest Files on System..."
	find / -type f -print 2>/dev/null -exec du -am {} + | sort -nr | head -5
fi

echo $notify
