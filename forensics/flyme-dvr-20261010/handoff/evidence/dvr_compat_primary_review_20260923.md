# DVR compatibility: independent primary review

Date: 2026-09-23. Offline only; no vehicle connection or writes.

## Verified sample separation

- Legacy Neusoft: old_lynk06_fw/system_ext4extract/build.prop lines 20–40: E01, BX11QJ, full_bx11_qj_2wk, mt6735, Android 5.1. DVR_odex_strings.txt line 6 independently contains DVR_e01.apk and bx11_qj_2wk build paths. This does not establish exact trim/module equivalence to the user's vehicle.
- Current OTA: files/vendor/build.prop lines 9–27: se1000, BX11-A3, bx11_3c3222_module, Android 11. Saved live baseline identity from flyme_phone_probe_20260920 confirms androidboot.hardware=se1000.
- The 'old' input in vhal_evs_diff/reconstruct_send_tables.py is flyme_vehicle_hal_probe_20260527, NOT the Neusoft firmware. Matching early/current Flyme send tables cannot prove legacy Neusoft protocol compatibility.

## Independently confirmed missed candidate

vhal_send_tables_reconstructed.json new_250.rows[136]: property 695206104 / 0x297000D8, can_id 424 / 0x1A8. Current VehicleProperty.java line 793 names this INFO_ID_MMI_DVRENTER. The is_dvr=false label comes from the extraction script restricting its DVR set to CA..D3; it is not evidence that the enter entry is unrelated to DVR.

This is a current-Flyme compiled enter-path candidate. This review does not independently establish whether the table's can_id denotes the final wire CAN frame or an MCU-level signal identifier. It also does not establish legal payload values, routing, or a responding recorder. Do not treat this as a ready-to-send frame.

## Compatibility verdict

Legacy APK/framework/driver reuse is not established and is unlikely to be drop-in across these platforms. External DVR protocol compatibility remains unknown. Legacy preview at 1910x710 plus RPC touch-coordinate forwarding supports an external display/control architecture, but does not identify the current module or establish common wiring. Lack of Android Camera/SD visibility cannot rule out an independent recorder.

Next discriminating investigation: trace current D8 conversion/encoding and old sx_dvr_enter consumer/RPC mapping, compare actual command semantics and module identity. Stop using shared DVR names or matching two Flyme builds as cross-platform compatibility evidence.
