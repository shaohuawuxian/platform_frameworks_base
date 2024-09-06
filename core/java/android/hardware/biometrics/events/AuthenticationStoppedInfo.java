/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.hardware.biometrics.events;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.hardware.biometrics.BiometricRequestConstants;
import android.hardware.biometrics.BiometricSourceType;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.util.DataClass;

/**
 * Information about a request to stop biometric authentication
 * @hide
 */
@DataClass(
        genParcelable = true,
        genAidl = true,
        genBuilder = true,
        genSetters = true,
        genEqualsHashCode = true
)
public final class AuthenticationStoppedInfo implements Parcelable {
    /** Identifies {@link BiometricSourceType} of authentication. */
    @NonNull
    private final BiometricSourceType mBiometricSourceType;

    /** Indicates reason from {@link BiometricRequestConstants.RequestReason} for
     * requesting authentication. */
    @BiometricRequestConstants.RequestReason
    private final int mRequestReason;



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/frameworks/base/core/java/android/hardware/biometrics/events/AuthenticationStoppedInfo.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    /* package-private */ AuthenticationStoppedInfo(
            @NonNull BiometricSourceType biometricSourceType,
            @BiometricRequestConstants.RequestReason int requestReason) {
        this.mBiometricSourceType = biometricSourceType;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, mBiometricSourceType);
        this.mRequestReason = requestReason;
        com.android.internal.util.AnnotationValidations.validate(
                BiometricRequestConstants.RequestReason.class, null, mRequestReason);

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * Identifies {@link BiometricSourceType} of authentication.
     */
    @DataClass.Generated.Member
    public @NonNull BiometricSourceType getBiometricSourceType() {
        return mBiometricSourceType;
    }

    /**
     * Indicates reason from {@link BiometricRequestConstants.RequestReason} for
     * requesting authentication.
     */
    @DataClass.Generated.Member
    public @BiometricRequestConstants.RequestReason int getRequestReason() {
        return mRequestReason;
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(AuthenticationStoppedInfo other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        AuthenticationStoppedInfo that = (AuthenticationStoppedInfo) o;
        //noinspection PointlessBooleanExpression
        return true
                && java.util.Objects.equals(mBiometricSourceType, that.mBiometricSourceType)
                && mRequestReason == that.mRequestReason;
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + java.util.Objects.hashCode(mBiometricSourceType);
        _hash = 31 * _hash + mRequestReason;
        return _hash;
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeTypedObject(mBiometricSourceType, flags);
        dest.writeInt(mRequestReason);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ AuthenticationStoppedInfo(@NonNull Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        BiometricSourceType biometricSourceType = (BiometricSourceType) in.readTypedObject(BiometricSourceType.CREATOR);
        int requestReason = in.readInt();

        this.mBiometricSourceType = biometricSourceType;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, mBiometricSourceType);
        this.mRequestReason = requestReason;
        com.android.internal.util.AnnotationValidations.validate(
                BiometricRequestConstants.RequestReason.class, null, mRequestReason);

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<AuthenticationStoppedInfo> CREATOR
            = new Parcelable.Creator<AuthenticationStoppedInfo>() {
        @Override
        public AuthenticationStoppedInfo[] newArray(int size) {
            return new AuthenticationStoppedInfo[size];
        }

        @Override
        public AuthenticationStoppedInfo createFromParcel(@NonNull Parcel in) {
            return new AuthenticationStoppedInfo(in);
        }
    };

    /**
     * A builder for {@link AuthenticationStoppedInfo}
     */
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private @NonNull BiometricSourceType mBiometricSourceType;
        private @BiometricRequestConstants.RequestReason int mRequestReason;

        private long mBuilderFieldsSet = 0L;

        /**
         * Creates a new Builder.
         *
         * @param biometricSourceType
         *   Identifies {@link BiometricSourceType} of authentication.
         * @param requestReason
         *   Indicates reason from {@link BiometricRequestConstants.RequestReason} for
         *   requesting authentication.
         */
        public Builder(
                @NonNull BiometricSourceType biometricSourceType,
                @BiometricRequestConstants.RequestReason int requestReason) {
            mBiometricSourceType = biometricSourceType;
            com.android.internal.util.AnnotationValidations.validate(
                    NonNull.class, null, mBiometricSourceType);
            mRequestReason = requestReason;
            com.android.internal.util.AnnotationValidations.validate(
                    BiometricRequestConstants.RequestReason.class, null, mRequestReason);
        }

        /**
         * Identifies {@link BiometricSourceType} of authentication.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setBiometricSourceType(@NonNull BiometricSourceType value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x1;
            mBiometricSourceType = value;
            return this;
        }

        /**
         * Indicates reason from {@link BiometricRequestConstants.RequestReason} for
         * requesting authentication.
         */
        @DataClass.Generated.Member
        public @NonNull Builder setRequestReason(@BiometricRequestConstants.RequestReason int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x2;
            mRequestReason = value;
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        public @NonNull AuthenticationStoppedInfo build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4; // Mark builder used

            AuthenticationStoppedInfo o = new AuthenticationStoppedInfo(
                    mBiometricSourceType,
                    mRequestReason);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x4) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    @DataClass.Generated(
            time = 1713305502581L,
            codegenVersion = "1.0.23",
            sourceFile = "frameworks/base/core/java/android/hardware/biometrics/events/AuthenticationStoppedInfo.java",
            inputSignatures = "private final @android.annotation.NonNull android.hardware.biometrics.BiometricSourceType mBiometricSourceType\nprivate final @android.hardware.biometrics.BiometricRequestConstants.RequestReason int mRequestReason\nclass AuthenticationStoppedInfo extends java.lang.Object implements [android.os.Parcelable]\n@com.android.internal.util.DataClass(genParcelable=true, genAidl=true, genBuilder=true, genSetters=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
