# Two-handed katana BLOCK / GUARD pose: references and candidate poses

Scope: research only. No code was edited, no game was run, nothing was downloaded.
Space: Blockbench, character faces -Z, own right is +X, Y up, pixels, origin at floor centre.
Shoulders at (+-5, 22, 0), straight-arm reach to the grip point = 9.5.

## 1. References (own-words summaries)

Web coverage was thin for game/anime block animations: no source I could open described the exact
Sekiro, Dark Souls or Epic Fight guard silhouette, so those items are marked "not verified".

| # | Source | What it says (paraphrase) | Use for us |
|---|--------|---------------------------|-----------|
| 1 | https://toyamaryu.org/kihon_fundamentals_kamae.htm (Toyama-ryu batto-do, a real katana school) | Chudan: both hands pushed forward at about navel height, left hand at the navel, elbows only slightly bent, tip at throat height. Hasso: blade upright, guard (tsuba) near ear height, body turned about 45 deg, arms relaxed with elbows pointing down. Jodan: hands above the head, blade about 45 deg back. Wakigamae: blade low at the side, body turned about 45 deg. | Gives hand height and blade angle for the three candidate families. |
| 2 | https://en.wikipedia.org/wiki/Hass%C5%8D-no-kamae | Hasso: hilt in front of the right shoulder, blade pointing nearly straight up, leaning slightly back; left foot forward. Same stance is called in-no-kamae or hassou in other schools; the German "vom Tag" is the equivalent. | Basis of Pose A. Hands at the right shoulder line, blade vertical. |
| 3 | https://en.wikipedia.org/wiki/Ch%C5%ABdan-no-kamae | Chudan: sword held in front of the body at chest-to-waist height, tip on the opponent's throat or eyes (seigan). The stance shields torso and right wrist, and is the neutral "defend or attack" guard. | Basis of Pose B (raised chudan). |
| 4 | https://akban.org/wiki/Japanese_sword_combat_stances | Overview of chudan, jodan, gedan, hasso, waki. Chudan = balanced attack/defence, shoulders relaxed; gedan = low, mostly used to parry and counter; jodan/hasso = offensive. | Confirms which guards read as "defensive". |
| 5 | https://kampaibudokai.org/Itto.htm | Itto-ryu kata 4/5/8 describe uke-nagashi (deflect-and-slide block): defender steps off-line, blade meets the cut, then counters. No hand/blade angles given. | Block is a deflection, not a flat wall; the blade should be angled, not square to the attacker. |
| 6 | https://thearma.org/essays/StancesIntro.htm and https://swordfight.uk/principal-guards-of-the-longsword/ | Western longsword guards: Ochs (hands at forehead, blade diagonal, tip toward the throat), Pflug (hands low at the hip, tip at eye level), vom Tag (blade up over the shoulder at about 45 deg). Two-handed, straight-ish arms, blade as a diagonal. | Cross-check: all two-handed guards keep the blade as a diagonal or vertical line with hands near the body centre line. |
| 7 | https://github.com/ZsoltMolnarrr/BetterCombat (project page) | Better Combat stores per-weapon idle poses and attack animations as data (resource folder player_animations); two-handed weapons are flagged in weapon attributes; parry is only a listed idea. Assumes items are held sword-style (flipping the model is needed for odd orientations). | Confirms the approach (pose = data keyframes) but gives no block pose to copy. Not verified: any actual block animation. |
| 8 | https://sekiroshadowsdietwice.wiki.fextralife.com/Deflection | Sekiro guard is hold-to-guard, deflect is a tap just before impact; Wolf brings both hands together on the sword. Wiki gives no pose geometry. | Gameplay idea only. Pose geometry not verified. |
| 9 | https://darksouls.fandom.com/wiki/Stance_(Greatsword) and https://darksouls.wiki.fextralife.com/Greatsword | Greatsword is effective at blocking when two-handed; the stance art holds the blade pointed at the enemy at shoulder height on the left. No block pose geometry. | Not verified. |
| 10 | Epic Fight / Valour Guard, https://www.modpackindex.com/mod/66709/epic-fight-valour-guard | Add-on overrides greatsword idle, walk, run and guard animations in Epic Fight (separate guard animation per weapon class). | Mod precedent: a dedicated guard animation per weapon, not a reused attack frame. |
| 11 | Anime (Bleach: Ichigo vs Byakuya, Soul Society arc) | In the fights the blocks are mostly one-sided: sword pulled up diagonally in front of the chest, blade tilted, and there are sword locks with the blade held at about 45 deg across the body. No usable still found via search; recommended to check the episode manually. Not verified. | Visual mood only. |

Key take-away from the references: every two-handed guard has (a) both hands near the body centre line
at chest-to-belly height, (b) the blade as a clean single line, either near-vertical (hasso) or about
45 deg diagonal (chudan raised, Ochs, vom Tag), and (c) very little elbow flare. A straight-arm model
can only do this honestly with the hands close together and low-to-mid, as computed below.

## 2. Geometry constraints for straight arms (computed)

Assumptions: grip spacing 3.2 px (hands 1.6 px each side of the midpoint, along the blade), visible
blade+grip beyond the lead hand about 17 px, eye at (0, 28.8, 0), vertical FOV 70 deg (about +-35 deg
up/down, about +-55 deg left/right at 16:9). Torso yaw sign: positive = turn left, negative = turn
right (right shoulder moves back). Azimuth: positive = left of straight ahead, negative = right.

1. Because the shoulders are 10 px apart and each arm is rigid at 9.5, both hands can only meet near
   the x = 0 plane on the circle (y-22)^2 + z^2 = 65 (z = -8.1 at chest height, z = -5.4 at y = 16).
   Hands cannot be pulled back toward the body: at chest height the hands are always about 7.5-8 px in front of the shoulders.
2. A hand pair that sits to the right of centre needs torso yaw to the right (about -7.5 deg for
   x = +1.8, about -15 deg for x = +2.5).
3. With the right hand leading (toward the tsuba), a blade tilted to the right (negative azimuth)
   gives uncrossed arms. A blade tilted to the left by up to about 25 deg still works but the right
   hand ends up about 0.5-1 px left of the left hand (arms slightly crossed at the wrists).
   Beyond about 30 deg left the crossing looks wrong.
4. In first person the hands at chest height project 30-60 deg below the eye, so they sit at or below
   the bottom edge of the screen; only the lower blade and the tip are in view. A blade that points
   left from a hand pair near x = 0 to +2.5 passes through the screen centre, which must be avoided.
5. IMPORTANT: vanilla first-person hand/item rendering is a separate camera-space transform, not the
   third-person rig. The FP projection numbers below are a check for a camera-space equivalent; if the
   mod draws FP with its own arm transform, apply the same relative layout (hands low and right of
   centre, blade rising on the centre-right) there.

## 3. Candidate poses

All three solved numerically so both hand points lie exactly 9.5 from their shoulders.
R = right-hand grip point, L = left-hand grip point (blade lead hand is R).

### Pose A: Hasso-guard (high vertical guard at the right shoulder line)  [RECOMMENDED]
- R = (1.9, 24.1, -8.1), L = (1.7, 20.9, -7.3). Hands are 3.2 px apart along the blade.
- Blade: azimuth -8 deg (tip 8 deg to the right of straight ahead), elevation 76 deg.
- Torso yaw: -7.6 deg (turned slightly right). Head can stay straight.
- Arm directions (unit vectors from shoulder): right arm (-0.33, 0.22, -0.92), about 13 deg up and 20 deg
  inward; left arm (0.71, -0.11, -0.70), about 6 deg down and 45 deg inward (V shaped, hands meet).
- Third person: reads clearly as a ready/guard stance, a clean vertical line beside the face, hands at
  chest height; from the side the blade is a vertical bar in front of the shoulder, from behind it
  sits to the right of the head. Matches the real hasso (hilt before right shoulder).
- First person: hands near the bottom edge (R about 13 deg right, 30 deg below eye), blade passes about 12 deg to
  the right of the screen centre and rises off the top of the screen. Does not cover the crosshair; reads
  as a vertical line on the centre-right. Weakness: it is near vertical, not a diagonal.
- Tuning: raise the blade lean (more negative azimuth, e.g. -15) to get a visible diagonal on the right
  at the cost of a bit more arm spread. Use el 70-76; below 65 the tip drifts toward the centre.

### Pose B: Raised chudan (diagonal "sloped block")
- R = (1.8, 20.3, -8.2), L = (1.2, 17.7, -6.4).
- Blade: azimuth -20 deg (tip to the right), elevation 55 deg.
- Torso yaw: -7.2 deg.
- Arm directions: right (-0.33, -0.18, -0.93), left (0.65, -0.45, -0.61).
- Third person: reads as a classic katana ready guard with tip high; the blade is a clear diagonal
  from belly to above the right shoulder; hands at belly-chest height.
- First person: hands about 46-60 deg below eye (off screen), blade runs from the bottom edge about 13 deg right
  of centre up to about 17 deg right, tip about 17 deg above the horizon; closest approach to centre about 16 deg.
  Visible as a slanted line on the centre-right. Slightly more "block-like" than A in FP.
- Weakness: reads a bit more like "stance" than "block"; the blade being angled away from the body leaves
  the left side visually open.

### Pose C: Cross-body diagonal guard (tip up-left, jodan-uke-like cover)  [not recommended]
- R = (0.2, 21.4, -8.3), L = (0.8, 18.6, -6.7).
- Blade: azimuth +20 deg (tip to the left), elevation 60 deg.
- Torso yaw: about 0 deg.
- Arm directions: right (-0.50, -0.06, -0.86), left (0.61, -0.36, -0.71); right hand ends about 0.6 px left of
  the left hand (slight wrist crossing).
- Third person: the strongest "body shield" read of the three, the blade covers the torso diagonally.
- First person: bad. Blade rises through the screen centre (closest approach about 6 deg) and sits on the
  left. Fails the "do not cover centre / keep blade on the right" requirement.
- Could be kept as a third-person-only variant if FP uses a separate pose.

## 4. Recommendation

Pose A (hasso-guard): hands R (1.9, 24.1, -8.1) and L (1.7, 20.9, -7.3), blade azimuth -8 deg (tip 8 deg
right of ahead), elevation 76 deg, torso yaw -7.6 deg (turn right). If a stronger diagonal is wanted in
first person, bend toward Pose B (hands about 4 px lower, azimuth -20, elevation 55).
Keep the grip spacing at about 3.2 px and the tsuba at the right hand.
