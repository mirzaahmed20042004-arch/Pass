# OmniDrive — Full-Stack Android APK & Vehicle Soundboard

A full-stack mobile & web vehicle soundboard app (**20 vehicles, 30 tap-to-loop sounds**) built with **React + Vite + Tailwind CSS + Web Audio API**, **Node.js + Express + SQLite (`node:sqlite`)**, and **Capacitor Android** to produce a standalone signed Android APK.

## Included Vehicles & Tap-to-Loop Sounds

- **Cars & Supercars**:
  - **Toyota Supra MK4 (2JZ-GTE)**: Supra 2JZ Sound + Supra Turbo Flutter (*Stututu!*)
  - **Exotic Supercar**: Supercar V10 Scream + Launch & Crackles
  - **Street Sedan Car**: Car Driving Sound + Dual-Tone Car Horn
- **Motorcycles & Bikes**:
  - **Sportbike 1000cc**: Bike Engine Sound + Bike Horn
  - **Motocross Dirt Bike**: 2-Stroke Braap
- **Emergency Vehicles**:
  - **Police Car**: Police Siren (Wail & Yelp) + Police V8 Pursuit Sound
  - **Ambulance**: Ambulance Siren (Hi-Lo + Yelp) + Ambulance Diesel Sound
  - **Fire Brigade**: Fire Brigade Q2B Mechanical Siren + Fire Truck Diesel & Pump
- **Heavy & Transit**:
  - **18-Wheeler Semi Truck**: Truck Diesel & Jake Brake + Twin-Trumpet Truck Air Horn
  - **City Transit Bus**: Bus Diesel & Pneumatic Air Brake Hiss + Bus Horn
  - **Tractor & Bulldozer**: Heavy Earthmover Diesel
- **Flying & Aviation**:
  - **Twin-Blade Helicopter**: Authentic Rotor Blade-Slap & Turboshaft Whine
  - **Passenger Jet Plane**: Widebody Turbofan Spool & Jet Roar
  - **Supersonic Fighter Jet**: Afterburner Shock-Cell Crackle & Flyby
  - **Propeller Aircraft**: Twin-Propeller Chop & Radial Engine
  - **Quadcopter Drone**: 4-Motor Brushless Hover
- **Rail, Marine & Futuristic**:
  - **Locomotive Train**: Diesel Chug & Rail Clack + 5-Chime Train Horn
  - **Speedboat**: Offshore V8 & Water Wake
  - **Cargo Ship**: Deep Harbor Bass Foghorn
  - **Hyper EV / Hovercraft**: Futuristic Electric Warp

## Full-Stack Architecture

- `audio-generator/generate_sounds.py` — Procedural acoustic synthesizer generating 30 seamless loopable MP3 files.
- `shared/catalog.json` — Shared vehicle & sound catalog used by both backend and frontend/APK.
- `backend/` — Node.js + Express + `node:sqlite` REST API (`/api/catalog`, `/api/sounds/:id/play`, `/api/favorites/:id/toggle`, `/api/presets`, `/api/stats`, `/api/apk-info`, `/download/OmniDrive-Vehicle-Sounds.apk`).
- `frontend/` — React + Tailwind CSS mobile-first soundboard with gapless Web Audio API `AudioBufferSourceNode` loop engine, live FFT spectrum visualizer, Multi-Mix vs Solo mode, and Capacitor Android project (`frontend/android/`).
- `.github/workflows/build-android-apk.yml` — Automated CI pipeline building, signing, verifying, and publishing `OmniDrive-Vehicle-Sounds.apk` to GitHub Releases.

## Direct Android APK Download

- **Direct GitHub Release APK Link**: `https://github.com/mirzaahmed20042004-arch/Pass/releases/download/v1.0.0-omnidrive/OmniDrive-Vehicle-Sounds.apk`

## Local Development

```bash
npm run install:all
npm run build:frontend
npm start
```
