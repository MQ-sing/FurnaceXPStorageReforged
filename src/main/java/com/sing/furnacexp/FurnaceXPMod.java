package com.sing.furnacexp;

import com.sing.furnacexp.furnace_xp_storage.Tags;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import net.minecraft.launchwrapper.IClassTransformer;

import net.minecraft.util.math.BlockPos;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;


import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;

@Mod(modid = Tags.MOD_ID)
public class FurnaceXPMod implements IFMLLoadingPlugin {
    public static int maxXp = 1;
    public static String UTILS_NAME = "com/sing/furnacexp/FurnaceXPMod$ExternalUtils";
    private static Configuration config;

    public static int getFinalXP(float xp) {
        int output = MathHelper.floor(xp);
        if (Math.random() < output) ++output;
        return output;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        final File configFile = new File(e.getModConfigurationDirectory(), Tags.MOD_ID + ".cfg");
        config = new Configuration(configFile);
        maxXp = config.get("general", "maxXp", maxXp).getInt();
        config.save();
    }

    @SubscribeEvent
    public void configurationChanged(ConfigChangedEvent e) {
        if (!e.getModID().equals(Tags.MOD_ID) || !config.hasChanged()) return;
        maxXp = config.get("general", "maxXp", maxXp).getInt();
        config.save();
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{ASMTransformer.class.getName()};
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {

    }

    @SuppressWarnings("unused")
    public static class ExternalUtils {
        public static float getSmeltXP(ItemStack input) {
            final FurnaceRecipes instance = FurnaceRecipes.instance();
            float xp = instance.getSmeltingExperience(input);
            if (maxXp != 0 && xp < maxXp) xp = maxXp;
            xp *= input.getCount();
            return xp;
        }

        public static void generateExperienceOrbs(float xp, World world, double x, double y, double z) {
            int finalXP = getFinalXP(xp);
            while (finalXP > 0) {
                int k = EntityXPOrb.getXPSplit(finalXP);
                finalXP -= k;
                world.spawnEntity(new EntityXPOrb(world, x, y + 0.5D, z + 0.5D, k));
            }
        }

        public static void generateExperienceOrbs(float xp, World world, BlockPos pos) {
            generateExperienceOrbs(xp, world, pos.getX(), pos.getY(), pos.getZ());
        }
    }

    public static class ASMTransformer implements IClassTransformer {
        public static final String TILEENTITY_FURNACE = "net.minecraft.tileentity.TileEntityFurnace";
        public static final String BLOCK_FURNACE = "net.minecraft.block.BlockFurnace";
        public static final String ENTITY_PLAYER = "net/minecraft/entity/player/EntityPlayer";
        private static final boolean deobf = false;
        public static final String SMELT_ITEM = deobf ? "smeltItem" : "func_145949_j";
        public static final String BREAK_BLOCK = deobf ? "breakBlock" : "func_180663_b";
        public static final String ON_CRAFTING = deobf ? "onCrafting" : "func_75208_c";
        public static final String PLAYER = deobf ? "player" : "field_75229_a";

        @Override
        public byte[] transform(String name, String transformedName, byte[] basicClass) {
            switch (transformedName) {
                case TILEENTITY_FURNACE: {
                    ClassNode node = new ClassNode();
                    ClassReader reader = new ClassReader(basicClass);
                    reader.accept(node, 0);
                    node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "xp", "F", null, 0));
                    for (MethodNode method : node.methods) {
                        final String functionName = FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(name, method.name, method.desc);
                        switch (functionName) {
                            case SMELT_ITEM: {
                                int i = 0;
                                AbstractInsnNode targetInsn = null;
                                for (AbstractInsnNode insn : method.instructions.toArray()) {
                                    if (insn instanceof LabelNode && i++ == 11) {
                                        targetInsn = insn;
                                        break;
                                    }
                                }
                                if (targetInsn == null) throw new RuntimeException("Cannot find inject location!");
                                final int smeltingItemStack = 2;
                                final InsnList insnList = new InsnList();
                                insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                insnList.add(new InsnNode(Opcodes.DUP));
                                insnList.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/tileentity/TileEntityFurnace", "xp", "F"));
                                insnList.add(new VarInsnNode(Opcodes.ALOAD, smeltingItemStack));
                                insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, UTILS_NAME, "getSmeltXP", "(Lnet/minecraft/item/ItemStack;)F", false));
                                insnList.add(new InsnNode(Opcodes.FADD));
                                insnList.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/tileentity/TileEntityFurnace", "xp", "F"));
                                method.instructions.insertBefore(targetInsn, insnList);
                                break;
                            }
                            case deobf ? "writeToNBT" : "func_189515_b": {
                                final InsnList insnList = new InsnList();
                                insnList.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                insnList.add(new LdcInsnNode("XP"));
                                insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                insnList.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/tileentity/TileEntityFurnace", "xp", "F"));
                                insnList.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/nbt/NBTTagCompound", deobf ? "setFloat" : "func_74776_a", "(Ljava/lang/String;F)V", false));
                                method.instructions.insert(method.instructions.get(6), insnList);
                                break;
                            }
                            case deobf ? "readFromNBT" : "func_145839_a": {
                                final InsnList insnList = new InsnList();
                                insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                insnList.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                insnList.add(new LdcInsnNode("XP"));
                                insnList.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/nbt/NBTTagCompound", deobf ? "getFloat" : "func_74760_g", "(Ljava/lang/String;)F", false));
                                insnList.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/tileentity/TileEntityFurnace", "xp", "F"));
                                method.instructions.insert(method.instructions.get(6), insnList);
                                break;
                            }
                        }
                    }
                    ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                    node.accept(classWriter);
                    return classWriter.toByteArray();
                }
                case BLOCK_FURNACE: {
                    ClassNode node = new ClassNode();
                    ClassReader reader = new ClassReader(basicClass);
                    reader.accept(node, 0);
                    for (MethodNode method : node.methods) {
                        final String functionName = FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(name, method.name, method.desc);
                        if (functionName.equals(BREAK_BLOCK)) {
                            AbstractInsnNode target = null;
                            for (AbstractInsnNode insn : method.instructions.toArray()) {
                                if (insn instanceof TypeInsnNode && insn.getOpcode() == Opcodes.CHECKCAST) {
                                    target = insn;
                                }
                            }
                            if(target==null)throw new RuntimeException();
                            final InsnList insnList = new InsnList();
                            insnList.add(new InsnNode(Opcodes.DUP));
                            insnList.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/tileentity/TileEntityFurnace", "xp", "F"));
                            //worldIn
                            insnList.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            //pos
                            insnList.add(new VarInsnNode(Opcodes.ALOAD, 2));
                            insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, UTILS_NAME, "generateExperienceOrbs", "(FLnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V", false));
                            method.instructions.insert(target, insnList);
                        }
                    }
                    ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                    node.accept(classWriter);
                    return classWriter.toByteArray();
                }
                case "net.minecraft.inventory.SlotFurnaceOutput": {
                    ClassNode node = new ClassNode();
                    ClassReader reader = new ClassReader(basicClass);
                    reader.accept(node, 0);
                    for (MethodNode method : node.methods) {
                        final String functionName = FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(name, method.name, method.desc);
                        if (functionName.equals(ON_CRAFTING)) {
                            final InsnList insnList = new InsnList();
                            method.localVariables = new ArrayList<>();
                            final LabelNode startNode = new LabelNode();
                            final LabelNode endNode = new LabelNode();
                            method.localVariables.add(new LocalVariableNode("player", "Lnet/minecraft/entity/player/EntityPlayer;", null, startNode, endNode, 1));
                            insnList.add(startNode);
                            insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            insnList.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/inventory/SlotFurnaceOutput", PLAYER, "Lnet/minecraft/entity/player/EntityPlayer;"));
                            insnList.add(new VarInsnNode(Opcodes.ASTORE, 1));
                            insnList.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            insnList.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_PLAYER, deobf ? "world" : "field_70170_p", "Lnet/minecraft/world/World;"));
                            insnList.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/world/World", deobf ? "isRemote" : "field_72995_K", "Z"));
                            insnList.add(new JumpInsnNode(Opcodes.IFNE, endNode));
                            insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            insnList.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/inventory/SlotFurnaceOutput", deobf ? "inventory" : "field_75224_c", "Lnet/minecraft/inventory/IInventory;"));
                            insnList.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/tileentity/TileEntityFurnace"));
                            insnList.add(new InsnNode(Opcodes.DUP));
                            insnList.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/tileentity/TileEntityFurnace", "xp", "F"));
                            insnList.add(new InsnNode(Opcodes.SWAP));
                            insnList.add(new InsnNode(Opcodes.FCONST_0));
                            insnList.add(new FieldInsnNode(Opcodes.PUTFIELD,"net/minecraft/tileentity/TileEntityFurnace","xp","F"));
                            insnList.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            insnList.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_PLAYER, deobf ? "world" : "field_70170_p", "Lnet/minecraft/world/World;"));
                            insnList.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            insnList.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_PLAYER, deobf ? "posX" : "field_70165_t", "D"));
                            insnList.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            insnList.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_PLAYER, deobf ? "posY" : "field_70163_u", "D"));
                            insnList.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            insnList.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_PLAYER, deobf ? "posZ" : "field_70161_v", "D"));
                            insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, UTILS_NAME, "generateExperienceOrbs", "(FLnet/minecraft/world/World;DDD)V", false));
                            insnList.add(endNode);
                            insnList.add(new InsnNode(Opcodes.RETURN));
                            method.instructions = insnList;
                        }
                    }
                    ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                    node.accept(classWriter);
                    return classWriter.toByteArray();
                }
            }

            return basicClass;
        }
    }
}
