#!/system/bin/sh
trial=/data/local/tmp/dashcam-localfix-test
sleep 35
test -e "$trial/trial-active" || exit 0
newpid=$(cat "$trial/service.pid" 2>/dev/null)
if test -n "$newpid"; then
    for recordpid in $(pidof dashcamd); do
        ppid=$(awk '{print $4}' "/proc/$recordpid/stat" 2>/dev/null)
        if test "$ppid" = "$newpid"; then kill "$recordpid" 2>/dev/null; fi
    done
    kill "$newpid" 2>/dev/null
fi
setprop ctl.start vendor.evs-hal-mock
rm -f "$trial/trial-active"
echo watchdog_restored_init_service
