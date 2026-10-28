#!/bin/sh

date -s "2000-01-01 00:00:00"

export KOTEI_TOPDIR=/usr/local/bin/cluster-e04
export BUID_OSNAME=arm
export APCOM_TOPDIR=$KOTEI_TOPDIR/com/$BUID_OSNAME
export MIDDLEWARE=middleware
export HMI=weston

export XDG_RUNTIME_DIR=/etc/weston/weston_xdg_runtime_dir
export DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/200/bus
export WAYLAND_DISPLAY=wayland-1

export CM_CONFIG_PATH=$KOTEI_TOPDIR/etc/cm_config.json
export LD_LIBRARY_PATH=/usr/local/lib:/usr/lib:/lib:$APCOM_TOPDIR/lib
export LD_LIBRARY_PATH="/usr/local/bin/cluster-e04/com/arm/lib/ara/phm":$LD_LIBRARY_PATH
export LD_LIBRARY_PATH="/usr/local/bin/cluster-e04/com/arm/lib/ara/log":$LD_LIBRARY_PATH
export LD_LIBRARY_PATH="/usr/local/bin/cluster-e04/com/arm/lib/LSP/processCommunicationLib":$LD_LIBRARY_PATH
export LOG_MANIFEST="/usr/local/bin/cluster-e04/etc/log_manifest.json"

rm -rf /tmp/runtime*

cd /usr/local/bin/cluster-e04/com/arm/bin
./phmd -c /usr/local/bin/cluster-e04/etc/phm_monitoring_alive.json&

cd $APCOM_TOPDIR/bin/
./iox-roudi -c $KOTEI_TOPDIR/etc/roudi_config.toml &

cd $KOTEI_TOPDIR/$HMI
nice -n -20 ./weston-simple-egl &

cd $KOTEI_TOPDIR/$MIDDLEWARE/
nice -n -20 ./VehicleService &
sleep 0.5
nice -n -18 ./DFSService &

nice -n -20 ./MixtureService &
#./ConfigService &

nice -n -18 ./GaugeService &

nice -n -16 ./IVIService &

nice -n -16 ./TcServer &

nice -n -14 ./WarnServer &

nice -n -12 ./TelltaleService &

nice -n -12 ./ChimeService &

#/usr/bin/weston-ivi-surface-config.sh 5
