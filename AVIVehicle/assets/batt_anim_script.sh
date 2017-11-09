#!/system/bin/sh

# battery threshold before boot-up (in percent)
bthresh=10

# time to sleep between checks (in seconds)
sleeptime=600

# file that contains current battery level as integer between 0 and 100
cfi=/sys/class/power_supply/battery/capacity
# file that contains 1 if we're plugged in to AC, 0 if not
acfi=/sys/class/power_supply/battery/subsystem/ac/online

# if either file doesn't exist, just do normal sleep+boot
[ ! -f $cfi ] && sleep $sleeptime && /system/bin/reboot
[ ! -f $acfi ] && sleep $sleeptime && /system/bin/reboot

# populate capacity and AC variables
c=`cat $cfi`
ac=`cat $acfi`

# stop loop if we're not plugged into AC
until [ "$ac" -eq 0 ]
do
    # if capacity above threshold, boot up
    if [ "$c" -gt "$bthresh" ]; then
	/system/bin/reboot
    fi
    
    # wait some time before next check
    sleep $sleeptime

    # update capacity and AC variables
    c=`cat $cfi`
    ac=`cat $acfi`
done
