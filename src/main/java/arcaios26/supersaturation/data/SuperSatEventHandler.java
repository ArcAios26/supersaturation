package arcaios26.supersaturation.data;

import arcaios26.supersaturation.SuperSaturation;
import arcaios26.supersaturation.setup.Config;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Food;
import net.minecraft.util.FoodStats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SuperSatEventHandler {
    private static final Map<UUID, Float> lastSaturationLevels = new HashMap<UUID, Float>();
    private static final Map<UUID, Integer> lastHungerLevels = new HashMap<UUID, Integer>();

    public static Field saturationLevel = null;
    public static Field hungerLevel = null;

    public static void onAttachCapabilitiesEvent(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof PlayerEntity) {
            SuperSatProvider provider = new SuperSatProvider();
            event.addCapability(new ResourceLocation(SuperSaturation.MODID, "saturation"), provider);
            event.addListener(provider::invalidate);
        }
    }

    public static void onStart(LivingEntityUseItemEvent.Start event) {
        if (event.isCanceled() || !(event.getEntity() instanceof PlayerEntity)) return;

        PlayerEntity player = (PlayerEntity) event.getEntity();
        if (!player.world.isRemote) {
            if (lastSaturationLevels.containsKey(player.getUniqueID()))
                lastSaturationLevels.replace(player.getUniqueID(), player.getFoodStats().getSaturationLevel());
            else
                lastSaturationLevels.put(player.getUniqueID(), player.getFoodStats().getSaturationLevel());

            if (lastHungerLevels.containsKey(player.getUniqueID()))
                lastHungerLevels.replace(player.getUniqueID(), player.getFoodStats().getFoodLevel());
            else
                lastHungerLevels.put(player.getUniqueID(), player.getFoodStats().getFoodLevel());
        }
    }

    public static void onStop(LivingEntityUseItemEvent.Stop event) {
        if (event.isCanceled() || !(event.getEntity() instanceof PlayerEntity)) return;

        PlayerEntity player = (PlayerEntity) event.getEntity();
        if (!player.world.isRemote) {
            if (lastSaturationLevels.containsKey(player.getUniqueID()))
                lastSaturationLevels.remove(player.getUniqueID());

            if (lastHungerLevels.containsKey(player.getUniqueID()))
                lastHungerLevels.remove(player.getUniqueID());
        }
    }

    public static void onFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.isCanceled() || !event.getItem().getItem().isFood() || !(event.getEntity() instanceof PlayerEntity)) return;

        Food food = event.getItem().getItem().getFood();
        PlayerEntity player = (PlayerEntity) event.getEntity();
        if (!player.world.isRemote) {

            player.getCapability(CapabilitySuperSat.SUPER_SAT, null).ifPresent(sat -> {
                if ((sat.getSat() <= 0.0001 || Config.CANGAIN.get()) && lastSaturationLevels.containsKey(player.getUniqueID())) {
                    float foodSat = 2 * (food.getHealing() * food.getSaturation());
                    float addedSat = Math.min(20-lastSaturationLevels.get(player.getUniqueID()), foodSat);
                    int addedHunger = Math.min(20-lastHungerLevels.get(player.getUniqueID()), food.getHealing());

                    if (sat.getSat() < 0) sat.setSat(0);
                    if (sat.getHunger() < 0) sat.setHunger(0);

                    if (Config.HUNGEROVERFLOW.get()) {
                        if (Config.HUNGERTOSAT.get()) {
                            if (lastSaturationLevels.get(player.getUniqueID()) + addedSat == 20)
                                foodSat = foodSat + (food.getHealing() - addedHunger);
                            else {
                                float needed = 20 - (lastSaturationLevels.get(player.getUniqueID()) + addedSat);
                                float toAdd = Math.min(needed, (food.getHealing() - addedHunger));
                                if (saturationLevel == null) {
                                    try {
                                        saturationLevel = ObfuscationReflectionHelper.findField(FoodStats.class, "field_75125_b");
                                    } catch(java.lang.NoSuchMethodError e) {
                                        saturationLevel = ObfuscationReflectionHelper.findField(FoodStats.class, "foodSaturationLevel");
                                    } catch (java.lang.NoSuchFieldError e) {
                                        saturationLevel = ObfuscationReflectionHelper.findField(FoodStats.class, "foodSaturationLevel");
                                    }
                                }
                                try {
                                    saturationLevel.set(player.getFoodStats(), player.getFoodStats().getSaturationLevel() + toAdd);
                                } catch (IllegalArgumentException e) {
                                    e.printStackTrace();
                                } catch (IllegalAccessException e) {
                                    e.printStackTrace();
                                }

                                foodSat = foodSat + (food.getHealing() - addedHunger - toAdd);
                            }
                        }
                        else
                            sat.setHunger(food.getHealing() - addedHunger);
                    }

                    if (Config.CANGAIN.get())
                        sat.setSat(sat.getSat() + (foodSat - addedSat));
                    else
                        sat.setSat(foodSat - addedSat);

                }
                if (lastSaturationLevels.containsKey(player.getUniqueID()))
                    lastSaturationLevels.remove(player.getUniqueID());

                SuperSaturation.LOGGER.info("saturation: " + sat.getSat());
                SuperSaturation.LOGGER.info("hunger: " + sat.getHunger());
            });
        }
    }

    public static void onPlayerTickEvent(TickEvent.PlayerTickEvent event) {
        if (event.isCanceled()) return;

        PlayerEntity player = event.player;
        if (!player.world.isRemote) {
            FoodStats pf = player.getFoodStats();
            float curSat = pf.getSaturationLevel();
            float needed = 20.0f - curSat;
            if (needed > 0.0)
                player.getCapability(CapabilitySuperSat.SUPER_SAT, null).ifPresent(sat -> {
                    if (sat.getSat() > 0.0f) {
                        float toAdd = Math.min(needed, sat.getSat());
                        if (saturationLevel == null) {
                            try {
                                saturationLevel = ObfuscationReflectionHelper.findField(FoodStats.class, "field_75125_b");
                            } catch(java.lang.NoSuchMethodError e) {
                                saturationLevel = ObfuscationReflectionHelper.findField(FoodStats.class, "foodSaturationLevel");
                            } catch (java.lang.NoSuchFieldError e) { 
                                saturationLevel = ObfuscationReflectionHelper.findField(FoodStats.class, "foodSaturationLevel");
                            }
                        }
                        try {
                            saturationLevel.set(pf, curSat + toAdd);
                        } catch (IllegalArgumentException e) {
                            e.printStackTrace();
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }

                        sat.setSat(sat.getSat() - toAdd);
                    }
                });

            int curHunger = pf.getFoodLevel();
            int neededHunger = 20 - curHunger;
            if (neededHunger > 0)
                player.getCapability(CapabilitySuperSat.SUPER_SAT, null).ifPresent(sat -> {
                    int toAdd = Math.min(neededHunger, sat.getHunger());
                    if (hungerLevel == null) {
                        try {
                            hungerLevel = ObfuscationReflectionHelper.findField(FoodStats.class, "field_75127_a");
                        } catch(java.lang.NoSuchMethodError e) {
                            hungerLevel = ObfuscationReflectionHelper.findField(FoodStats.class, "foodLevel");
                        } catch(java.lang.NoSuchFieldError e) {
                            hungerLevel = ObfuscationReflectionHelper.findField(FoodStats.class, "foodLevel");
                        }
                        try {
                            hungerLevel.set(pf, curHunger + toAdd);
                        } catch (IllegalArgumentException e) {
                            e.printStackTrace();
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }

                        sat.setHunger(sat.getHunger() - toAdd);
                    }
                });
        }
    }
}
