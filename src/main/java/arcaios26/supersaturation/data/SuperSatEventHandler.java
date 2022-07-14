package arcaios26.supersaturation.data;

import arcaios26.supersaturation.SuperSaturation;
import arcaios26.supersaturation.setup.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Food;
import net.minecraft.util.FoodStats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
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
        if (!player.level.isClientSide) {
            if (lastSaturationLevels.containsKey(player.getUUID()))
                lastSaturationLevels.replace(player.getUUID(), player.getFoodData().getSaturationLevel());
            else
                lastSaturationLevels.put(player.getUUID(), player.getFoodData().getSaturationLevel());

            if (lastHungerLevels.containsKey(player.getUUID()))
                lastHungerLevels.replace(player.getUUID(), player.getFoodData().getFoodLevel());
            else
                lastHungerLevels.put(player.getUUID(), player.getFoodData().getFoodLevel());
        }
    }

    public static void onStop(LivingEntityUseItemEvent.Stop event) {
        if (event.isCanceled() || !(event.getEntity() instanceof PlayerEntity)) return;

        PlayerEntity player = (PlayerEntity) event.getEntity();
        if (!player.level.isClientSide) {
            if (lastSaturationLevels.containsKey(player.getUUID()))
                lastSaturationLevels.remove(player.getUUID());

            if (lastHungerLevels.containsKey(player.getUUID()))
                lastHungerLevels.remove(player.getUUID());
        }
    }

    public static void onFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.isCanceled() || !event.getItem().getItem().isEdible() || !(event.getEntity() instanceof PlayerEntity)) return;

        Food food = event.getItem().getItem().getFoodProperties();
        PlayerEntity player = (PlayerEntity) event.getEntity();
        if (!player.level.isClientSide) {

            player.getCapability(CapabilitySuperSat.SUPER_SAT, null).ifPresent(sat -> {
                if ((sat.getSat() <= 0.0001 || Config.CANGAIN.get()) && lastSaturationLevels.containsKey(player.getUUID())) {
                    float foodSat = 2 * (food.getNutrition() * food.getSaturationModifier());
                    float addedSat = Math.min(20-lastSaturationLevels.get(player.getUUID()), foodSat);
                    int addedHunger = Math.min(20-lastHungerLevels.get(player.getUUID()), food.getNutrition());

                    if (sat.getSat() < 0) sat.setSat(0);
                    if (sat.getHunger() < 0) sat.setHunger(0);

                    if (Config.HUNGEROVERFLOW.get()) {
                        if (Config.HUNGERTOSAT.get()) {
                            if (lastSaturationLevels.get(player.getUUID()) + addedSat == 20)
                                foodSat = foodSat + (food.getNutrition() - addedHunger);
                            else {
                                float needed = 20 - (lastSaturationLevels.get(player.getUUID()) + addedSat);
                                float toAdd = Math.min(needed, (food.getNutrition() - addedHunger));
                                if (saturationLevel == null) {
                                    try {
                                        saturationLevel = ObfuscationReflectionHelper.findField(FoodStats.class, "ield_75125_b");
                                    } catch(java.lang.NoSuchMethodError e) {
                                        saturationLevel = ObfuscationReflectionHelper.findField(FoodStats.class, "saturationLevel");
                                    } catch (java.lang.NoSuchFieldError e) {
                                        saturationLevel = ObfuscationReflectionHelper.findField(FoodStats.class, "saturationLevel");
                                    }
                                }
                                try {
                                    saturationLevel.set(player.getFoodData(), player.getFoodData().getSaturationLevel() + toAdd);
                                } catch (IllegalArgumentException e) {
                                    e.printStackTrace();
                                } catch (IllegalAccessException e) {
                                    e.printStackTrace();
                                }

                                foodSat = foodSat + (food.getNutrition() - addedHunger - toAdd);
                            }
                        }
                        else
                            sat.setHunger(food.getNutrition() - addedHunger);
                    }

                    if (Config.CANGAIN.get())
                        sat.setSat(sat.getSat() + (foodSat - addedSat));
                    else
                        sat.setSat(foodSat - addedSat);

                }
                if (lastSaturationLevels.containsKey(player.getUUID()))
                    lastSaturationLevels.remove(player.getUUID());

                SuperSaturation.LOGGER.info("saturation: " + sat.getSat());
                SuperSaturation.LOGGER.info("hunger: " + sat.getHunger());
            });
        }
    }

    public static void onPlayerTickEvent(TickEvent.PlayerTickEvent event) {
        if (event.isCanceled()) return;

        PlayerEntity player = event.player;
        if (!player.level.isClientSide) {
            FoodStats pf = player.getFoodData();
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
                                saturationLevel = ObfuscationReflectionHelper.findField(FoodStats.class, "saturationLevel");
                            } catch (java.lang.NoSuchFieldError e) { 
                                saturationLevel = ObfuscationReflectionHelper.findField(FoodStats.class, "saturationLevel");
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
            player.getCapability(CapabilitySuperSat.SUPER_SAT, null).ifPresent(cap -> cap.sync((ServerPlayerEntity) player));
        }
    }

}
