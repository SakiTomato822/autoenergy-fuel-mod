# AutoEnergy Fuel UI

Standalone fuel-energy display for the Lynk & Co / Flyme Auto head unit.

## Runtime modes

- On the vehicle, the app polls the real `CarPropertyManager` every five seconds.
- On a regular Android emulator without `android.car.Car`, it uses the built-in
  `SimulatedCarProperties` source.

The simulator is active only in emulator preview mode. It does not override real
vehicle data.

## Change a scenario without rebuilding

```powershell
adb shell am broadcast `
  -a com.lynk.autoenergyfuel.SIMULATE_CAR_PROPERTY `
  -p com.lynk.autoenergyfuel `
  --es scenario aggressive
```

Available scenarios:

- `normal`
- `aggressive`
- `low_fuel`
- `full`
- `sensor_fault`

## Inject one CarProperty

```powershell
adb shell am broadcast `
  -a com.lynk.autoenergyfuel.SIMULATE_CAR_PROPERTY `
  -p com.lynk.autoenergyfuel `
  --ei propertyId 4211968 `
  --ei areaId 0 `
  --es value 45
```

Use `--es value null` to simulate an unavailable property.

| Property | propertyId | areaId |
| --- | ---: | ---: |
| Fuel percent | `4211968` | `0` |
| Oil range | `1054720` | `0` |
| Total range | `291504904` | `0` |
| Odometer | `291504644` | `0` |
| Average fuel, trip 1 | `4194560` | `1` |
| Average fuel, trip 2 | `4194560` | `2` |
| Trip distance | `612373760` | `1` or `2` |
| Trip average speed | `612372992` | `1` or `2` |
| Trip duration | `612374016` | `1` or `2` |
