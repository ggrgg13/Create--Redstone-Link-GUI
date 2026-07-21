package com.ggrgg.createredstonelinkgui;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import com.ggrgg.createredstonelinkgui.common.network.CopyToPresetPayload;
import com.ggrgg.createredstonelinkgui.common.network.OpenLinkMenuPayload;
import com.ggrgg.createredstonelinkgui.common.network.PasteFromPresetPayload;
import com.ggrgg.createredstonelinkgui.common.network.PresetSlotUpdatePayload;
import com.ggrgg.createredstonelinkgui.common.network.RedstoneLinkFrequencyPayload;
import com.ggrgg.createredstonelinkgui.common.network.RedstoneLinkModeTogglePayload;
import com.ggrgg.createredstonelinkgui.common.network.RedstoneLinkMovePayload;
import com.ggrgg.createredstonelinkgui.common.network.TinyLinkFreqUpdatePayload;
import com.ggrgg.createredstonelinkgui.common.network.TinyLinkScreenSwapPayload;
import com.ggrgg.createredstonelinkgui.common.network.VectorThrusterFrequencyPayload;
import com.ggrgg.createredstonelinkgui.common.network.VoidLinkClaimPayload;
import com.ggrgg.createredstonelinkgui.common.preset.FrequencyPresetData;
import com.ggrgg.createredstonelinkgui.common.menu.RedstoneLinkMenu;
import com.ggrgg.createredstonelinkgui.common.menu.TinyRedstoneLinkMenu;
import com.ggrgg.createredstonelinkgui.common.menu.VectorThrusterLinkMenu;
import com.ggrgg.createredstonelinkgui.common.menu.VoidLinkMenu;
import com.ggrgg.createredstonelinkgui.client.screen.RedstoneLinkConfigScreen;
import com.ggrgg.createredstonelinkgui.client.screen.TinyRedstoneLinkConfigScreen;
import com.ggrgg.createredstonelinkgui.client.screen.VectorThrusterLinkConfigScreen;
import com.ggrgg.createredstonelinkgui.client.screen.VoidLinkConfigScreen;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CreateRedstoneLinkGUI.MODID)
public class CreateRedstoneLinkGUI {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "createredstonelinkgui";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    private final String networkProtocol;

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public CreateRedstoneLinkGUI(IEventBus modEventBus, ModContainer modContainer) {
        // Read the mod version from the injected ModContainer (sourced from gradle.properties via neoforge.mods.toml)
        this.networkProtocol = modContainer.getModInfo().getVersion().toString();
        
        // Register attachment types
        FrequencyPresetData.ATTACHMENT_TYPES.register(modEventBus);
        
        // Register core network configurations
        modEventBus.addListener(this::registerPackets);
        
        // Setup deferred registry items
        RedstoneLinkMenu.MENUS.register(modEventBus);
        VoidLinkMenu.MENUS.register(modEventBus);
        TinyRedstoneLinkMenu.MENUS.register(modEventBus);
        VectorThrusterLinkMenu.MENUS.register(modEventBus);
        
        // Client environment isolation check to block headless server errors
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::registerScreens);
            modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        }

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void registerPackets(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(networkProtocol);
        registrar.playToServer(
                RedstoneLinkFrequencyPayload.TYPE,
                RedstoneLinkFrequencyPayload.CODEC,
                RedstoneLinkFrequencyPayload::handleServer
        );
        registrar.playToServer(
                RedstoneLinkModeTogglePayload.TYPE,
                RedstoneLinkModeTogglePayload.CODEC,
                RedstoneLinkModeTogglePayload::handleServer
        );
        registrar.playToServer(
                RedstoneLinkMovePayload.TYPE,
                RedstoneLinkMovePayload.CODEC,
                RedstoneLinkMovePayload::handleServer
        );
        registrar.playToServer(
                VoidLinkClaimPayload.TYPE,
                VoidLinkClaimPayload.CODEC,
                VoidLinkClaimPayload::handleServer
        );
        registrar.playToServer(
                OpenLinkMenuPayload.TYPE,
                OpenLinkMenuPayload.CODEC,
                OpenLinkMenuPayload::handleServer
        );
        // Preset system packets
        registrar.playToServer(
                CopyToPresetPayload.TYPE,
                CopyToPresetPayload.CODEC,
                CopyToPresetPayload::handleServer
        );
        registrar.playToServer(
                PasteFromPresetPayload.TYPE,
                PasteFromPresetPayload.CODEC,
                PasteFromPresetPayload::handleServer
        );
        registrar.playToServer(
                PresetSlotUpdatePayload.TYPE,
                PresetSlotUpdatePayload.CODEC,
                PresetSlotUpdatePayload::handleServer
        );
        // TinyCreate compatibility packets
        registrar.playToServer(
                TinyLinkScreenSwapPayload.TYPE,
                TinyLinkScreenSwapPayload.CODEC,
                TinyLinkScreenSwapPayload::handleServer
        );
        registrar.playToServer(
                TinyLinkFreqUpdatePayload.TYPE,
                TinyLinkFreqUpdatePayload.CODEC,
                TinyLinkFreqUpdatePayload::handleServer
        );
        // Vector thruster compatibility packets
        registrar.playToServer(
                VectorThrusterFrequencyPayload.TYPE,
                VectorThrusterFrequencyPayload.CODEC,
                VectorThrusterFrequencyPayload::handleServer
        );
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(RedstoneLinkMenu.TYPE.get(), RedstoneLinkConfigScreen::new);
        event.register(VoidLinkMenu.TYPE.get(), VoidLinkConfigScreen::new);
        event.register(TinyRedstoneLinkMenu.TYPE.get(), TinyRedstoneLinkConfigScreen::new);
        event.register(VectorThrusterLinkMenu.TYPE.get(), VectorThrusterLinkConfigScreen::new);
    }
}