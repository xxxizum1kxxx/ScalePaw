/*
 * openScale
 * Copyright (C) 2025 olie.xdev <olie.xdeveloper@googlemail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.izum1k.scalepaw.ble.openscale

import com.izum1k.scalepaw.ble.openscale.scales.DebugGattHandler
import com.izum1k.scalepaw.ble.openscale.scales.DeviceSupport
import com.izum1k.scalepaw.ble.openscale.scales.AAAxHandler
import com.izum1k.scalepaw.ble.openscale.scales.ActiveEraBF06Handler
import com.izum1k.scalepaw.ble.openscale.scales.AfuB1Handler
import com.izum1k.scalepaw.ble.openscale.scales.AiLinkBroadcastHandler
import com.izum1k.scalepaw.ble.openscale.scales.AndUC352BLEHandler
import com.izum1k.scalepaw.ble.openscale.scales.BeurerBF450Handler
import com.izum1k.scalepaw.ble.openscale.scales.BeurerSanitasHandler
import com.izum1k.scalepaw.ble.openscale.scales.BodyConnectHandler
import com.izum1k.scalepaw.ble.openscale.scales.BodyPod2Handler
import com.izum1k.scalepaw.ble.openscale.scales.CultSmartScaleProHandler
import com.izum1k.scalepaw.ble.openscale.scales.CustomOpenScaleHandler
import com.izum1k.scalepaw.ble.openscale.scales.DigooDGSO38HHandler
import com.izum1k.scalepaw.ble.openscale.scales.DrTrustSSW532Handler
import com.izum1k.scalepaw.ble.openscale.scales.EEBBLHandler
import com.izum1k.scalepaw.ble.openscale.scales.ESCS20MHandler
import com.izum1k.scalepaw.ble.openscale.scales.EbelterBodyFatB2Handler
import com.izum1k.scalepaw.ble.openscale.scales.EtekcityESF551Handler
import com.izum1k.scalepaw.ble.openscale.scales.EtekcityFit8SHandler
import com.izum1k.scalepaw.ble.openscale.scales.EufyC20Handler
import com.izum1k.scalepaw.ble.openscale.scales.EufyP2Handler
import com.izum1k.scalepaw.ble.openscale.scales.ExcelvanCF36xHandler
import com.izum1k.scalepaw.ble.openscale.scales.ExingtechY1Handler
import com.izum1k.scalepaw.ble.openscale.scales.FitTrackDaraHandler
import com.izum1k.scalepaw.ble.openscale.scales.HealthKeep280Handler
import com.izum1k.scalepaw.ble.openscale.scales.HesleyHandler
import com.izum1k.scalepaw.ble.openscale.scales.HoffenBbs8107Handler
import com.izum1k.scalepaw.ble.openscale.scales.HuaweiAhCh100Handler
import com.izum1k.scalepaw.ble.openscale.scales.HuaweiCH100SHandler
import com.izum1k.scalepaw.ble.openscale.scales.HuaweiHagridWspHandler
import com.izum1k.scalepaw.ble.openscale.scales.HumeDara2Handler
import com.izum1k.scalepaw.ble.openscale.scales.IHealthHS3Handler
import com.izum1k.scalepaw.ble.openscale.scales.InlifeHandler
import com.izum1k.scalepaw.ble.openscale.scales.KeepS3Handler
import com.izum1k.scalepaw.ble.openscale.scales.MGBHandler
import com.izum1k.scalepaw.ble.openscale.scales.MedisanaBs44xHandler
import com.izum1k.scalepaw.ble.openscale.scales.MiScaleHandler
import com.izum1k.scalepaw.ble.openscale.scales.MiScaleS400Handler
import com.izum1k.scalepaw.ble.openscale.scales.OkOkHandler
import com.izum1k.scalepaw.ble.openscale.scales.OmronWlcHandler
import com.izum1k.scalepaw.ble.openscale.scales.OneByoneHandler
import com.izum1k.scalepaw.ble.openscale.scales.OneByoneNewHandler
import com.izum1k.scalepaw.ble.openscale.scales.PicoocBroadcastHandler
import com.izum1k.scalepaw.ble.openscale.scales.PicoocHandler
import com.izum1k.scalepaw.ble.openscale.scales.QNHandler
import com.izum1k.scalepaw.ble.openscale.scales.QNHandlerBroadcast
import com.izum1k.scalepaw.ble.openscale.scales.QardioBaseHandler
import com.izum1k.scalepaw.ble.openscale.scales.RealmeSmartScaleHandler
import com.izum1k.scalepaw.ble.openscale.scales.RedmondSkybalanceHandler
import com.izum1k.scalepaw.ble.openscale.scales.RelaxmedicHandler
import com.izum1k.scalepaw.ble.openscale.scales.RenphoES26BBHandler
import com.izum1k.scalepaw.ble.openscale.scales.RenphoHandler
import com.izum1k.scalepaw.ble.openscale.scales.RobiS9Handler
import com.izum1k.scalepaw.ble.openscale.scales.RunstarR5Handler
import com.izum1k.scalepaw.ble.openscale.scales.RunstarR6Handler
import com.izum1k.scalepaw.ble.openscale.scales.RyFitHandler
import com.izum1k.scalepaw.ble.openscale.scales.SanitasSbf72Handler
import com.izum1k.scalepaw.ble.openscale.scales.ScaleupHandler
import com.izum1k.scalepaw.ble.openscale.scales.SenssunHandler
import com.izum1k.scalepaw.ble.openscale.scales.SinocareHandler
import com.izum1k.scalepaw.ble.openscale.scales.SoehnleHandler
import com.izum1k.scalepaw.ble.openscale.scales.StandardBeurerSanitasHandler
import com.izum1k.scalepaw.ble.openscale.scales.TaylorBIAHandler
import com.izum1k.scalepaw.ble.openscale.scales.TrisaBodyAnalyzeHandler
import com.izum1k.scalepaw.ble.openscale.scales.VitafitVT701Handler
import com.izum1k.scalepaw.ble.openscale.scales.WeightGurusA3Handler
import com.izum1k.scalepaw.ble.openscale.scales.XiaomiS800Handler
import com.izum1k.scalepaw.ble.openscale.scales.YunmaiHandler
import com.izum1k.scalepaw.ble.openscale.scales.YunmaiXHandler
import com.izum1k.scalepaw.ble.openscale.scales.ScaleDeviceHandler
import com.izum1k.scalepaw.ble.withings.WithingsHandler

/** The driver registry: which handler claims a scanned device. */
object ScaleFactory {
    /**
     * Order matters: the FIRST handler whose [ScaleDeviceHandler.supportFor] is non-null wins.
     * TaylorBIAHandler, FitTrackDaraHandler, RelaxmedicHandler, RobiS9Handler and
     * DrTrustSSW532Handler must stay ahead of MGBHandler — all live on service 0xFFB0, which
     * MGBHandler matches on its own.
     */
    fun createHandlers(): List<ScaleDeviceHandler> = listOf(
        WithingsHandler(),
        AndUC352BLEHandler(),
        HealthKeep280Handler(),
        PicoocHandler(),
        // Exact PICOOC-L match must precede ScaleupHandler's generic 0xD0 manufacturer match.
        PicoocBroadcastHandler(),
        ActiveEraBF06Handler(),
        AfuB1Handler(),
        KeepS3Handler(),
        OmronWlcHandler(),
        BeurerBF450Handler(),
        TaylorBIAHandler(),
        RyFitHandler(),
        CultSmartScaleProHandler(),
        RealmeSmartScaleHandler(),
        RedmondSkybalanceHandler(),
        YunmaiHandler(isMini = false),
        YunmaiHandler(isMini = true),
        YunmaiXHandler(),
        TrisaBodyAnalyzeHandler(),
        SanitasSbf72Handler(),
        StandardBeurerSanitasHandler(),
        SoehnleHandler(),
        SinocareHandler(),
        SenssunHandler(),
        RenphoHandler(),
        AiLinkBroadcastHandler(),
        QNHandlerBroadcast(),
        QNHandler(),
        OneByoneHandler(),
        OneByoneNewHandler(),
        OkOkHandler(),
        MiScaleS400Handler(),
        XiaomiS800Handler(),
        MiScaleHandler(),
        RunstarR6Handler(),
        RunstarR5Handler(),
        RelaxmedicHandler(),
        RobiS9Handler(),
        VitafitVT701Handler(),
        EEBBLHandler(),
        FitTrackDaraHandler(),
        DrTrustSSW532Handler(),
        AAAxHandler(),
        MGBHandler(),
        MedisanaBs44xHandler(),
        InlifeHandler(),
        IHealthHS3Handler(),
        HuaweiAhCh100Handler(),
        HuaweiCH100SHandler(),
        HuaweiHagridWspHandler(),
        HoffenBbs8107Handler(),
        HesleyHandler(),
        ExingtechY1Handler(),
        EbelterBodyFatB2Handler(),
        HumeDara2Handler(),
        QardioBaseHandler(),
        ExcelvanCF36xHandler(),
        EtekcityESF551Handler(),
        EtekcityFit8SHandler(),
        EufyC20Handler(),
        EufyP2Handler(),
        ESCS20MHandler(),
        RenphoES26BBHandler(),
        DigooDGSO38HHandler(),
        CustomOpenScaleHandler(),
        BeurerSanitasHandler(),
        ScaleupHandler(),
        BodyConnectHandler(),
        BodyPod2Handler(),
        WeightGurusA3Handler(),
    )

    /** Shared instances for support checks only; sessions always get fresh handlers. */
    private val probes by lazy { createHandlers() }

    /** The support descriptor of the first driver claiming [device]. */
    fun supportFor(device: ScannedDeviceInfo): DeviceSupport? =
        probes.firstNotNullOfOrNull { runCatching { it.supportFor(device) }.getOrNull() }

    /** A fresh handler and its support descriptor for [device], or null when no driver claims it. */
    fun resolve(device: ScannedDeviceInfo, debug: Boolean = false): Pair<ScaleDeviceHandler, DeviceSupport>? {
        if (debug) return DebugGattHandler() to DebugGattHandler.SUPPORT
        val index = probes.indexOfFirst { runCatching { it.supportFor(device) }.getOrNull() != null }
        if (index < 0) return null
        val handler = createHandlers()[index]
        val support = handler.supportFor(device) ?: return null
        return handler to support
    }
}
