# DVR enter path: primary bounded review

Date 2026-09-23. Offline only. Luna's attempted continuation was interrupted by a usage-limit error before producing a report; this note is the primary agent's independent read of existing evidence.

## Current property and table

`apk_out/android_car_framework_jadx/sources/android/hardware/automotive/vehicle/V2_0/VehicleProperty.java:793` names decimal 695206104 (`0x297000D8`) `INFO_ID_MMI_DVRENTER`. The complete reconstructed 2.5.0 send initializer table (`vhal_evs_diff/vhal_send_tables_reconstructed.json`, `new_250.rows[136]`) pairs it with `can_id=424 (0x1A8)`. Direct initializer disassembly stores the property at 0x300F7C and `0x1A8` at 0x300F78. The table generator's `is_dvr=false` label is due to its deliberately narrow CA..D3 filter.

The 2.5.0 CP cluster debug data has an 18-byte `ST_MMI_DVR_Command` with `MMI_DVREnter` at offset 8 (`cluster_dvr_dwarf.json`). This is a separate bus/service structure, not proof that Android D8 reaches the CP cluster or identifies the external recorder.

The Android framework also names `0x297000D7` `INFO_ID_MMI_DVR_LVDS_MODE_CONFIG` (`VehicleProperty.java:794`), and the CP structure has a separate `MMI_DVR_LVDSModeConfig` byte at offset 6. However, `0x297000D7` is **absent** from the complete reconstructed 2.5.0 VHAL send initializer table; D8 is present. A framework constant or CP member therefore does not by itself establish an Android-to-MCU route for LVDS switching.

## Legacy request/response outline (not a protocol mapping)

The old Neusoft `DVRLogin` app sets Android `Settings.System` `sx_dvr_enter` to 1 when its DVR icon is clicked and the prior value is 0 (`apk_out/old_DVRLogin_dex_jadx/sources/com/neusoft/dvrlogin/MainActivity.java:93-99`). In the old DVR app, `StateManager.a(int, byte[])` handles a `DVR_ControlRequest` callback: subtype `bArr[1] == 1` plus nonzero `sx_dvr_enter` schedules the DVR activity's entry handler; subtype 3 exits (`apk_out/old_DVR_dex_jadx/sources/com/neusoft/cardvr/e/a.java:82-111`). This is stronger than a shared name: the legacy UI waits for a recorder/control state callback before entering. The source does not identify that callback's physical CAN frame or establish that legacy subtype 1 equals any current VHAL command value. Current 2.5.0 has inbound `DVR_ControlRequest` at property `0x297000D3` on reconstructed `0x2B1`, but the car's observed `0x2B1` bytes were all zero, so there is still no verified live transition or compatibility proof.

## Bounded call path and wire boundary

The existing `vhal_send_path_diff.json` recovered-symbol disassembly shows:

- `ModuleDataManager::convertPropValToCanSignal` at decimal VA 2944148 calls `VehicleDataBuilder::encapsulateCANData` at 3094540 and the send declaration lookup at 3096332. After lookup, instructions at 2945860–2945868 load the table's `+0x20` 32-bit value and store its lower 16 bits on the stack. For D8, this is 0x1A8.
- `McuManager::sendCanMessage` at 1610984 tail-branches to `CanManager::sendCanMessage` at 1936320 (`0x1D8BC0`).
- `CanManager::sendCanMessage` calls helper 1941048 (`0x1D9E38`). Direct disassembly of that helper shows a `0x28`-byte element stride and vector allocation/copy operations; it is a vector insert helper, not itself the MCU frame encoder. In the caller, 1936892 writes a one-byte argument to `Message+0x28`, 1936924 writes the count of 40-byte `CanSignalModel` records to `Message+0x29`, and 1936928 inserts those records. The caller then invokes a virtual method on Message, `Message::setCmdCtrl(3)`, and `McuManager::sendMessage` at 1936976. These are instruction-level observations, not a complete Message wire schema.

This is evidence of a VHAL → CAN-signal declaration → MCU Message path. The table field is labelled CAN ID in the reconstruction and is loaded as a 16-bit value, but the exact final MCU packet layout and arbitration seen by an external ECU require disassembly of the helper and frame encoder or a passive bus trace. Calling 0x1A8 a proven external bus arbitration ID is premature.

## Video side

`com.ecarx.parking.AvmEnterService` reads UB954 `lock_status` at `/sys/devices/platform/e95c0000.i2c/i2c-7/7-003d/lock_status` and MAX96722 status at `/sys/devices/platform/e95e0000.i2c/i2c-9/9-006b/max96722_status` (source lines 60–63, 277–304, 370–373). This is an observable video-link condition for AVM. The recovered EVS ELF exposes `/dev/video6cif` and `/dev/video12cif` strings. Searches of recovered parking Java sources and CarService/AdapterAPIImpl Java sources found no runtime call to D8 or DVR LVDS property; they expose constants, not a matched video switch. Search absence is not proof that a native or CP-side link is impossible.

## Remaining decisive checks

1. Trace the virtual Message method and `McuManager::sendMessage` serialization to establish whether 0x1A8 survives as a wire arbitration ID and where the payload goes. The helper at 0x1D9E38 is already identified as vector insertion.
2. Recover D8's per-signal bit start, length, and permissible values from current VHAL signal declarations, and compare with CP-side `MMI_DVREnter` semantics. No active command should be sent based on the name alone.
3. Establish whether D8 changes video source: identify any handler, lock status transition, or EVS camera-ID behavior, preferably through passive observation of a naturally occurring event.
