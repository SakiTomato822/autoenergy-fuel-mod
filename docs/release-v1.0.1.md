# AutoEnergy Fuel 1.0.1

This patch keeps the 1.0.0 read-only fuel data path and adds the four requested finishing pieces:

- a persistent light/dark theme switch on both the energy page and mileage statistics page;
- a dedicated AutoEnergy launcher icon;
- JSON export/import from Diagnostics, plus filename-based recovery of `Download/AutoEnergyData/history-*.json` after reinstall;
- shape-preserving cubic interpolation for the fuel trend instead of a hard polyline.

The APK is built and signed by GitHub Actions with the existing upgrade-compatible debug certificate. No GPS permission is added.
