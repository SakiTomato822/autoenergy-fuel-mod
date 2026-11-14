#!/system/bin/sh
# Bounded parked-car experiment; no vehicle-control properties are written.
trial=/data/local/tmp/dashcam-localfix-test
service=vendor.evs-hal-mock
binary=/vendor/bin/hw/android.hardware.automotive.evs@1.0-service
mkdir -p "$trial"
test "$(id -u)" = 0 || exit 1
test "$(getprop init.svc.$service)" = running || exit 2
test ! -e "$trial/trial-active" || exit 3
touch "$trial/trial-active"
echo $$ > "$trial/controller.pid"
cleanup() {
    trap - EXIT INT TERM HUP
    if test -f "$trial/service.pid"; then
        newpid=$(cat "$trial/service.pid")
        # Only stop the recorder spawned for this experiment.
        for recordpid in $(pidof dashcamd); do
            ppid=$(awk '{print $4}' "/proc/$recordpid/stat" 2>/dev/null)
            if test "$ppid" = "$newpid"; then kill "$recordpid" 2>/dev/null; fi
        done
        kill "$newpid" 2>/dev/null
    fi
    sleep 1
    setprop ctl.start "$service"
    rm -f "$trial/trial-active"
}
trap cleanup EXIT INT TERM HUP
# This watchdog survives loss of the controlling ADB shell.
setsid /system/bin/sh "$trial/watchdog.sh" </dev/null >"$trial/watchdog.log" 2>&1 &
setprop ctl.stop "$service"
sleep 1
if test "$(getprop init.svc.$service)" != stopped; then exit 4; fi
LD_PRELOAD=/vendor/lib64/libevsproxy_preload.so "$binary" >"$trial/evs-console.log" 2>&1 &
newpid=$!
echo "$newpid" > "$trial/service.pid"
logcat -b main -v threadtime --pid="$newpid" >"$trial/evs-live.log" 2>&1 &
logpid=$!
sleep 2
am start -W -n com.ecarx.parking/.MainAvmActivity >"$trial/avm-launch.txt" 2>&1
sleep 8
ps -A -o PID,PPID,STAT,NAME | grep -E 'evs|dashcam'
for recordpid in $(pidof dashcamd); do
    cat "/proc/$recordpid/stat" 2>/dev/null
    logcat -b main -d -v threadtime --pid="$recordpid" >"$trial/daemon-live.log" 2>&1
done
find /data/media/0/DashCam -maxdepth 2 -type f -printf '%p %s\n' 2>/dev/null | head -30
kill "$logpid" 2>/dev/null
cleanup
sleep 2
getprop init.svc.$service
echo original_init_service_restarted
