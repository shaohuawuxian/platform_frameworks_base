/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.server.broadcastradio.aidl;

import android.hardware.broadcastradio.IdentifierType;
import android.hardware.broadcastradio.Metadata;
import android.hardware.broadcastradio.ProgramIdentifier;
import android.hardware.broadcastradio.ProgramInfo;
import android.hardware.broadcastradio.VendorKeyValue;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioMetadata;
import android.util.ArrayMap;

final class AidlTestUtils {

    private AidlTestUtils() {
        throw new UnsupportedOperationException("AidlTestUtils class is noninstantiable");
    }

    static RadioManager.ModuleProperties makeDefaultModuleProperties() {
        return new RadioManager.ModuleProperties(
                /* id= */ 0, /* serviceName= */ "", /* classId= */ 0, /* implementor= */ "",
                /* product= */ "", /* version= */ "", /* serial= */ "", /* numTuners= */ 0,
                /* numAudioSources= */ 0, /* isInitializationRequired= */ false,
                /* isCaptureSupported= */ false, /* bands= */ null,
                /* isBgScanSupported= */ false, new int[] {}, new int[] {},
                /* dabFrequencyTable= */ null, /* vendorInfo= */ null);
    }

    static RadioManager.ProgramInfo makeProgramInfo(ProgramSelector selector, int signalQuality) {
        return new RadioManager.ProgramInfo(selector,
                selector.getPrimaryId(), selector.getPrimaryId(), /* relatedContents= */ null,
                /* infoFlags= */ 0, signalQuality,
                new RadioMetadata.Builder().build(), new ArrayMap<>());
    }

    static RadioManager.ProgramInfo makeProgramInfo(int programType,
            ProgramSelector.Identifier identifier, int signalQuality) {
        ProgramSelector selector = makeProgramSelector(programType, identifier);
        return makeProgramInfo(selector, signalQuality);
    }

    static ProgramIdentifier makeHalIdentifier(@IdentifierType int type, long value) {
        ProgramIdentifier halDabId = new ProgramIdentifier();
        halDabId.type = type;
        halDabId.value = value;
        return halDabId;
    }

    static ProgramSelector makeFmSelector(long freq) {
        return makeProgramSelector(ProgramSelector.PROGRAM_TYPE_FM,
                new ProgramSelector.Identifier(ProgramSelector.IDENTIFIER_TYPE_AMFM_FREQUENCY,
                        freq));
    }

    static ProgramSelector makeProgramSelector(int programType,
            ProgramSelector.Identifier identifier) {
        return new ProgramSelector(programType, identifier, /* secondaryIds= */ null,
                /* vendorIds= */ null);
    }

    static android.hardware.broadcastradio.ProgramSelector makeHalFmSelector(int freq) {
        ProgramIdentifier halId = makeHalIdentifier(IdentifierType.AMFM_FREQUENCY_KHZ, freq);
        return makeHalSelector(halId, /* secondaryIds= */ new ProgramIdentifier[0]);
    }

    static android.hardware.broadcastradio.ProgramSelector makeHalSelector(
            ProgramIdentifier primaryId, ProgramIdentifier[] secondaryIds) {
        android.hardware.broadcastradio.ProgramSelector hwSelector =
                new android.hardware.broadcastradio.ProgramSelector();
        hwSelector.primaryId = primaryId;
        hwSelector.secondaryIds = secondaryIds;
        return hwSelector;
    }

    static ProgramInfo programInfoToHalProgramInfo(RadioManager.ProgramInfo info) {
        // Note that because ConversionUtils does not by design provide functions for all
        // conversions, this function only copies fields that are set by makeProgramInfo().
        ProgramInfo hwInfo = new ProgramInfo();
        hwInfo.selector = ConversionUtils.programSelectorToHalProgramSelector(info.getSelector());
        hwInfo.logicallyTunedTo =
                ConversionUtils.identifierToHalProgramIdentifier(info.getLogicallyTunedTo());
        hwInfo.physicallyTunedTo =
                ConversionUtils.identifierToHalProgramIdentifier(info.getPhysicallyTunedTo());
        hwInfo.signalQuality = info.getSignalStrength();
        hwInfo.relatedContent = new ProgramIdentifier[]{};
        hwInfo.metadata = new Metadata[]{};
        return hwInfo;
    }

    static ProgramInfo makeHalProgramInfo(
            android.hardware.broadcastradio.ProgramSelector hwSel, int hwSignalQuality) {
        return makeHalProgramInfo(hwSel, hwSel.primaryId, hwSel.primaryId, hwSignalQuality);
    }

    static ProgramInfo makeHalProgramInfo(
            android.hardware.broadcastradio.ProgramSelector hwSel,
            ProgramIdentifier logicallyTunedTo, ProgramIdentifier physicallyTunedTo,
            int hwSignalQuality) {
        ProgramInfo hwInfo = new ProgramInfo();
        hwInfo.selector = hwSel;
        hwInfo.logicallyTunedTo = logicallyTunedTo;
        hwInfo.physicallyTunedTo = physicallyTunedTo;
        hwInfo.signalQuality = hwSignalQuality;
        hwInfo.relatedContent = new ProgramIdentifier[]{};
        hwInfo.metadata = new Metadata[]{};
        return hwInfo;
    }

    static VendorKeyValue makeVendorKeyValue(String vendorKey, String vendorValue) {
        VendorKeyValue vendorKeyValue = new VendorKeyValue();
        vendorKeyValue.key = vendorKey;
        vendorKeyValue.value = vendorValue;
        return vendorKeyValue;
    }

    static android.hardware.broadcastradio.Announcement makeAnnouncement(int type,
            int selectorFreq) {
        android.hardware.broadcastradio.Announcement halAnnouncement =
                new android.hardware.broadcastradio.Announcement();
        halAnnouncement.type = (byte) type;
        halAnnouncement.selector = makeHalFmSelector(selectorFreq);
        halAnnouncement.vendorInfo = new VendorKeyValue[]{};
        return halAnnouncement;
    }
}
