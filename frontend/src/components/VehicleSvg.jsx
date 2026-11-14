import React from 'react';

export function VehicleSvg({ type, accent = '#f59e0b', active = false }) {
  const stroke = active ? '#ffffff' : accent;
  const glow = active ? `drop-shadow(0 0 10px ${accent})` : 'none';

  const common = {
    viewBox: '0 0 120 64',
    className: 'w-20 h-11 transition-transform duration-300',
    style: { filter: glow }
  };

  switch (type) {
    case 'supra':
      return (
        <svg {...common} fill="none">
          {/* Iconic MK4 Supra curved rear wing & sleek coupe silhouette */}
          <path d="M12 41 L14 28 L22 28 L20 40" stroke={stroke} strokeWidth="2.5" strokeLinecap="round" />
          <path d="M10 27 L25 27" stroke={stroke} strokeWidth="3" strokeLinecap="round" />
          <path d="M16 42 Q26 30 44 26 L70 25 Q88 29 104 37 L107 43 L14 43 Z" stroke={stroke} strokeWidth="2.4" fill={`${accent}22`} strokeLinejoin="round" />
          <path d="M40 27 L50 20 L74 20 L86 28" stroke={stroke} strokeWidth="2.2" strokeLinejoin="round" />
          <circle cx="33" cy="44" r="8" fill="#0c101b" stroke={stroke} strokeWidth="2.6" />
          <circle cx="33" cy="44" r="3" fill={stroke} />
          <circle cx="88" cy="44" r="8" fill="#0c101b" stroke={stroke} strokeWidth="2.6" />
          <circle cx="88" cy="44" r="3" fill={stroke} />
          <path d="M98 37 L105 37" stroke="#fef08a" strokeWidth="2.5" strokeLinecap="round" />
        </svg>
      );
    case 'supercar':
      return (
        <svg {...common} fill="none">
          <path d="M10 42 L18 33 L40 24 L68 23 L94 31 L109 39 L106 44 L12 44 Z" stroke={stroke} strokeWidth="2.4" fill={`${accent}22`} strokeLinejoin="round" />
          <path d="M44 25 L56 19 L74 20 L85 28" stroke={stroke} strokeWidth="2" />
          <path d="M14 33 L8 28 L18 28" stroke={stroke} strokeWidth="2.2" strokeLinecap="round" />
          <circle cx="32" cy="44" r="8" fill="#0c101b" stroke={stroke} strokeWidth="2.6" />
          <circle cx="90" cy="44" r="8" fill="#0c101b" stroke={stroke} strokeWidth="2.6" />
          <path d="M56 35 L70 32 L68 38 Z" fill={stroke} opacity="0.6" />
        </svg>
      );
    case 'car':
      return (
        <svg {...common} fill="none">
          <path d="M16 42 L18 33 L34 29 L46 19 L76 19 L89 29 L103 33 L105 42 Z" stroke={stroke} strokeWidth="2.4" fill={`${accent}22`} strokeLinejoin="round" />
          <path d="M48 22 L73 22 L83 29 L38 29 Z" stroke={stroke} strokeWidth="1.8" opacity="0.8" />
          <circle cx="34" cy="43" r="7.5" fill="#0c101b" stroke={stroke} strokeWidth="2.6" />
          <circle cx="87" cy="43" r="7.5" fill="#0c101b" stroke={stroke} strokeWidth="2.6" />
        </svg>
      );
    case 'bike':
    case 'dirtbike':
      return (
        <svg {...common} fill="none">
          <circle cx="28" cy="44" r="10" fill="#0c101b" stroke={stroke} strokeWidth="2.6" />
          <circle cx="90" cy="44" r="10" fill="#0c101b" stroke={stroke} strokeWidth="2.6" />
          <path d="M28 44 L48 30 L68 30 L80 20 L90 44" stroke={stroke} strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M24 27 L44 27 L55 34 L73 26" stroke={stroke} strokeWidth="2.6" strokeLinecap="round" />
          <path d="M76 17 L86 17" stroke={stroke} strokeWidth="2.6" strokeLinecap="round" />
        </svg>
      );
    case 'police':
      return (
        <svg {...common} fill="none">
          <path d="M16 42 L18 33 L34 29 L46 19 L76 19 L89 29 L103 33 L105 42 Z" stroke={stroke} strokeWidth="2.4" fill={`${accent}22`} strokeLinejoin="round" />
          {/* Lightbar */}
          <rect x="52" y="13" width="16" height="5" rx="2" fill="#ef4444" stroke="#60a5fa" strokeWidth="1.5" />
          <circle cx="34" cy="43" r="7.5" fill="#0c101b" stroke={stroke} strokeWidth="2.6" />
          <circle cx="87" cy="43" r="7.5" fill="#0c101b" stroke={stroke} strokeWidth="2.6" />
        </svg>
      );
    case 'ambulance':
      return (
        <svg {...common} fill="none">
          <path d="M14 43 L14 19 L76 19 L85 28 L102 31 L104 43 Z" stroke={stroke} strokeWidth="2.4" fill={`${accent}22`} strokeLinejoin="round" />
          <rect x="75" y="13" width="10" height="5" rx="1.5" fill="#ef4444" />
          <path d="M42 25 V35 M37 30 H47" stroke="#fb7185" strokeWidth="3" strokeLinecap="round" />
          <circle cx="33" cy="44" r="7.5" fill="#0c101b" stroke={stroke} strokeWidth="2.6" />
          <circle cx="88" cy="44" r="7.5" fill="#0c101b" stroke={stroke} strokeWidth="2.6" />
        </svg>
      );
    case 'firebrigade':
      return (
        <svg {...common} fill="none">
          <path d="M12 43 L12 20 L84 20 L92 27 L105 30 L105 43 Z" stroke={stroke} strokeWidth="2.4" fill={`${accent}26`} strokeLinejoin="round" />
          {/* Ladder on top */}
          <path d="M16 16 L80 16 M22 16 V20 M34 16 V20 M46 16 V20 M58 16 V20 M70 16 V20" stroke="#fbbf24" strokeWidth="2" />
          <circle cx="30" cy="44" r="7.5" fill="#0c101b" stroke={stroke} strokeWidth="2.6" />
          <circle cx="52" cy="44" r="7.5" fill="#0c101b" stroke={stroke} strokeWidth="2.6" />
          <circle cx="91" cy="44" r="7.5" fill="#0c101b" stroke={stroke} strokeWidth="2.6" />
        </svg>
      );
    case 'truck':
      return (
        <svg {...common} fill="none">
          <rect x="10" y="16" width="60" height="25" rx="2" stroke={stroke} strokeWidth="2.2" fill={`${accent}1c`} />
          <path d="M72 41 L72 22 L90 22 L100 31 L106 33 L106 41 Z" stroke={stroke} strokeWidth="2.4" fill={`${accent}28`} strokeLinejoin="round" />
          <circle cx="24" cy="44" r="7" fill="#0c101b" stroke={stroke} strokeWidth="2.5" />
          <circle cx="42" cy="44" r="7" fill="#0c101b" stroke={stroke} strokeWidth="2.5" />
          <circle cx="90" cy="44" r="7" fill="#0c101b" stroke={stroke} strokeWidth="2.5" />
        </svg>
      );
    case 'bus':
      return (
        <svg {...common} fill="none">
          <rect x="12" y="16" width="94" height="27" rx="5" stroke={stroke} strokeWidth="2.4" fill={`${accent}20`} />
          <path d="M20 22 H36 V31 H20 Z M42 22 H58 V31 H42 Z M64 22 H80 V31 H64 Z M86 22 H99 V33 H86 Z" stroke={stroke} strokeWidth="1.7" />
          <circle cx="32" cy="45" r="7" fill="#0c101b" stroke={stroke} strokeWidth="2.5" />
          <circle cx="86" cy="45" r="7" fill="#0c101b" stroke={stroke} strokeWidth="2.5" />
        </svg>
      );
    case 'tractor':
      return (
        <svg {...common} fill="none">
          <path d="M30 38 L30 18 L54 18 L58 29 L92 29 L96 40 L30 40 Z" stroke={stroke} strokeWidth="2.4" fill={`${accent}22`} strokeLinejoin="round" />
          <path d="M74 29 V17" stroke={stroke} strokeWidth="2.6" strokeLinecap="round" />
          <circle cx="38" cy="42" r="11" fill="#0c101b" stroke={stroke} strokeWidth="2.8" />
          <circle cx="86" cy="45" r="7.5" fill="#0c101b" stroke={stroke} strokeWidth="2.5" />
        </svg>
      );
    case 'helicopter':
      return (
        <svg {...common} fill="none">
          {/* Main rotor */}
          <path d="M18 12 H102" stroke={stroke} strokeWidth="2.6" strokeLinecap="round" />
          <path d="M60 12 V22" stroke={stroke} strokeWidth="2.6" />
          {/* Cabin + tail boom */}
          <path d="M16 28 L44 28 Q56 18 76 22 Q94 25 96 36 Q94 42 74 42 L44 42 L36 33 L16 33 Z" stroke={stroke} strokeWidth="2.4" fill={`${accent}24`} strokeLinejoin="round" />
          <path d="M16 20 V36" stroke={stroke} strokeWidth="2.2" strokeLinecap="round" />
          {/* Skids */}
          <path d="M46 49 H92 M56 42 L54 49 M82 42 L84 49" stroke={stroke} strokeWidth="2.3" strokeLinecap="round" />
        </svg>
      );
    case 'plane':
      return (
        <svg {...common} fill="none">
          <path d="M12 32 L26 30 L48 30 L34 12 L44 12 L68 30 L96 30 Q108 32 108 36 Q108 40 96 40 L66 40 L46 53 L36 53 L48 40 L22 40 L14 45 L10 45 L15 36 Z" stroke={stroke} strokeWidth="2.2" fill={`${accent}22`} strokeLinejoin="round" />
        </svg>
      );
    case 'fighter_jet':
      return (
        <svg {...common} fill="none">
          <path d="M12 34 L34 30 L52 13 L60 13 L56 30 L108 35 L56 40 L60 54 L52 54 L34 38 Z" stroke={stroke} strokeWidth="2.2" fill={`${accent}28`} strokeLinejoin="round" />
          <path d="M74 32 Q86 32 92 35 Q86 38 74 38" stroke="#fef08a" strokeWidth="1.8" />
        </svg>
      );
    case 'prop_plane':
      return (
        <svg {...common} fill="none">
          <path d="M18 33 L88 28 L94 35 L88 41 L18 38 Z" stroke={stroke} strokeWidth="2.2" fill={`${accent}22`} strokeLinejoin="round" />
          <path d="M48 16 L64 29 M48 52 L64 40" stroke={stroke} strokeWidth="2.4" strokeLinecap="round" />
          <path d="M98 18 V50" stroke="#fef08a" strokeWidth="2.5" strokeLinecap="round" />
        </svg>
      );
    case 'drone':
      return (
        <svg {...common} fill="none">
          <path d="M20 22 H44 M76 22 H100" stroke={stroke} strokeWidth="2.5" strokeLinecap="round" />
          <path d="M32 22 L46 34 H74 L88 22" stroke={stroke} strokeWidth="2.4" strokeLinejoin="round" />
          <rect x="46" y="31" width="28" height="11" rx="4" stroke={stroke} strokeWidth="2.2" fill={`${accent}24`} />
          <path d="M44 48 H76 M50 42 L46 48 M70 42 L74 48" stroke={stroke} strokeWidth="2" strokeLinecap="round" />
        </svg>
      );
    case 'train':
      return (
        <svg {...common} fill="none">
          <path d="M14 42 L14 20 L88 20 Q104 20 106 34 L106 42 Z" stroke={stroke} strokeWidth="2.4" fill={`${accent}22`} strokeLinejoin="round" />
          <path d="M10 49 H110" stroke={stroke} strokeWidth="2" strokeDasharray="6 4" />
          <circle cx="28" cy="44" r="5" fill={stroke} />
          <circle cx="46" cy="44" r="5" fill={stroke} />
          <circle cx="72" cy="44" r="5" fill={stroke} />
          <circle cx="90" cy="44" r="5" fill={stroke} />
        </svg>
      );
    case 'speedboat':
      return (
        <svg {...common} fill="none">
          <path d="M14 40 L22 29 L52 29 L62 22 L76 28 L106 33 L94 42 Z" stroke={stroke} strokeWidth="2.4" fill={`${accent}22`} strokeLinejoin="round" />
          <path d="M10 48 Q30 43 50 48 T90 48 T112 46" stroke="#38bdf8" strokeWidth="2" strokeLinecap="round" />
        </svg>
      );
    case 'ship':
      return (
        <svg {...common} fill="none">
          <path d="M14 34 H98 L90 47 H22 Z" stroke={stroke} strokeWidth="2.4" fill={`${accent}22`} strokeLinejoin="round" />
          <rect x="30" y="21" width="24" height="13" stroke={stroke} strokeWidth="2" />
          <rect x="38" y="13" width="8" height="8" stroke={stroke} strokeWidth="2" />
          <path d="M12 52 Q36 47 60 52 T108 52" stroke="#60a5fa" strokeWidth="2" strokeLinecap="round" />
        </svg>
      );
    default:
      return (
        <svg {...common} fill="none">
          <path d="M14 39 L30 27 L82 25 L106 34 L98 42 L20 42 Z" stroke={stroke} strokeWidth="2.4" fill={`${accent}26`} strokeLinejoin="round" />
          <path d="M26 48 H94" stroke="#f472b6" strokeWidth="2" strokeDasharray="8 4" strokeLinecap="round" />
        </svg>
      );
  }
}
