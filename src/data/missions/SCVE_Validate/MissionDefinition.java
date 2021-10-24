package data.missions.SCVE_Validate;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static data.scripts.SCVE_ComparatorUtils.memberComparator;
import static data.scripts.SCVE_ModPlugin.allModules;
import static data.scripts.SCVE_Utils.*;

public class MissionDefinition implements MissionDefinitionPlugin {

    private final Logger log = Global.getLogger(MissionDefinition.class);

    @Override
    public void defineMission(MissionDefinitionAPI api) {
        // initialize
        initializeMission(api, getString("validateTagline"), null);

        HashMap<String, Integer> maxCapsAndVents = getMaxCapsAndVents();
        TreeMap<FleetMemberAPI, String> badFleetMemberMap = new TreeMap<>(memberComparator);
        FleetMemberAPI member;
        for (String variantId : Global.getSettings().getAllVariantIds()) {
            String error = "";
            ShipVariantAPI variant = Global.getSettings().getVariant(variantId);
            // skip for certain reasons
            // // skip variants that Alex hardcoded into the game
            if (variant.getVariantFilePath() == null) {
                continue;
            }
            // // skip variants that will crash the mission upon addition
            ArrayList<String> hullSpecWeaponSlotIds = new ArrayList<>();
            for (WeaponSlotAPI weaponSlot : variant.getHullSpec().getAllWeaponSlotsCopy()) {
                hullSpecWeaponSlotIds.add(weaponSlot.getId());
            }
            if (!hullSpecWeaponSlotIds.containsAll(variant.getFittedWeaponSlots())) {
                log.error("ERROR: VARIANT " + variantId + " IS FITTING SLOTS THAT AREN'T IN THE HULL SPEC");
                continue;
            }
            if (!validateHullSpecExcludingFighters(variant.getHullSpec(), allModules)) {
                continue;
            }
            // check for over max OP
            if (variant.getUnusedOP(null) < 0 && !variant.isFighter()) {
                error += getString("validateOverMaxOP") + Math.abs(variant.getUnusedOP(null)) + ", ";
            }
            /* todo too many false positives atm */
            // check for under max OP
            if (variant.getUnusedOP(null) > 0 && !variant.isFighter()) {
                error += getString("validateUnderMaxOP") + Math.abs(variant.getUnusedOP(null)) + ", ";
            }
            // check for over max flux capacitors/vents
            if (variant.getNumFluxCapacitors() > maxCapsAndVents.get(variant.getHullSize().toString())) {
                error += getString("validateOverMaxCaps") + (variant.getNumFluxCapacitors() - maxCapsAndVents.get(variant.getHullSize().toString())) + ", ";
            }
            if (variant.getNumFluxVents() > maxCapsAndVents.get(variant.getHullSize().toString())) {
                error += getString("validateOverMaxVents") + (variant.getNumFluxVents() - maxCapsAndVents.get(variant.getHullSize().toString())) + ", ";
            }
            /* todo too many false positives atm
            // check for hiddenEverywhere hullmods
            ArrayList<String> hullModIds = new ArrayList<>();
            for (String hullModId : variant.getHullMods()) {
                HullModSpecAPI hullModSpec = Global.getSettings().getHullModSpec(hullModId);
                if (hullModSpec.isHiddenEverywhere() && !variant.isFighter()) {
                    hullModIds.add(hullModId);
                }
            }
            if (!hullModIds.isEmpty()) {
                error += getString("validateHiddenEverywhere") + hullModIds + ", ";
            }
            // check for permaMods not in hullSpec, ignoring sMods
            ArrayList<String> permaModIds = new ArrayList<>();
            for (String permaModId : variant.getPermaMods()) {
                if (variant.getSMods().contains(permaModId)) {
                    continue;
                }
                if (variant.getHullSpec().getBuiltInMods().contains(permaModId)) {
                    permaModIds.add(permaModId);
                }
            }
            if (!permaModIds.isEmpty()) {
                error += getString("validatePermaMods") + permaModIds + ", ";
            }
            // check for suppressedMods
            if (!variant.getSuppressedMods().isEmpty()) {
                error += getString("validateSuppressedMods") + variant.getSuppressedMods() + ", ";
            }
             */
            // check for weapons in wrong slot type/size
            if (!variant.isFighter()) {
                ArrayList<String> invalidWeaponSlotIds = new ArrayList<>();
                ArrayList<String> hiddenSlotIds = new ArrayList<>();
                for (String slotId : variant.getFittedWeaponSlots()) {
                    WeaponSlotAPI slot = variant.getSlot(slotId);
                    WeaponSpecAPI weaponSpec = variant.getWeaponSpec(slotId);
                    if (slot.isBuiltIn()) {
                        continue;
                    }
                    if (!slot.weaponFits(weaponSpec)) {
                        invalidWeaponSlotIds.add(slotId);
                    }
                    if (slot.isHidden()) {
                        hiddenSlotIds.add(slotId);
                    }
                }
                if (!invalidWeaponSlotIds.isEmpty()) {
                    error += getString("validateWeapons") + invalidWeaponSlotIds + ", ";
                }
                if (!hiddenSlotIds.isEmpty()) {
                    error += getString("validateHiddenMounts") + hiddenSlotIds + ", ";
                }
            } else {
                // check for <1 efficiency shields on fighters
                if (variant.getHullSpec().getShieldType().equals(ShieldAPI.ShieldType.NONE)
                        && variant.getHullSpec().getShieldType().equals(ShieldAPI.ShieldType.PHASE)
                        && variant.getHullSpec().getShieldSpec().getFluxPerDamageAbsorbed() < 1f) {
                    error += getString("validateFighterShields") + variant.getHullSpec().getShieldSpec().getFluxPerDamageAbsorbed() + ", ";
                }
            }
            if (!error.isEmpty()) {
                member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
                badFleetMemberMap.put(member, error);
            }
        }
        log.info("-----------------");
        log.info("INVALID VARIANTS:");
        log.info("-----------------");
        for (Map.Entry<FleetMemberAPI, String> badMember : badFleetMemberMap.entrySet()) {
            String variantId = badMember.getKey().getVariant().getHullVariantId();
            log.info(variantId + ": " + badMember.getValue());
            FleetMemberAPI ship = api.addToFleet(FleetSide.PLAYER, variantId, FleetMemberType.SHIP, badMember.getValue(), false);
        }
        log.info("-----------------");
    }

    public static HashMap<String, Integer> getMaxCapsAndVents() {
        HashMap<String, Integer> maxCapsAndVents = new HashMap<>();
        maxCapsAndVents.put(ShipAPI.HullSize.FRIGATE.toString(), 10);
        maxCapsAndVents.put(ShipAPI.HullSize.DESTROYER.toString(), 20);
        maxCapsAndVents.put(ShipAPI.HullSize.CRUISER.toString(), 30);
        maxCapsAndVents.put(ShipAPI.HullSize.CAPITAL_SHIP.toString(), 50);
        maxCapsAndVents.put(ShipAPI.HullSize.FIGHTER.toString(), 0);
        maxCapsAndVents.put(ShipAPI.HullSize.DEFAULT.toString(), 0);
        return maxCapsAndVents;
    }
}
