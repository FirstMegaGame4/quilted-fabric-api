/*
 * Copyright 2022 The Quilt Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.mixin.object.builder;

import org.quiltmc.qsl.vehicle.api.MinecartComparatorLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.block.BlockState;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.BlockPos;

import net.fabricmc.fabric.api.object.builder.v1.entity.MinecartComparatorLogicRegistry;

@Mixin(MinecartComparatorLogic.class)
public interface MinecartComparatorLogicMixin {
	@Inject(method = "getComparatorValue", at = @At("HEAD"), cancellable = true)
	default void modifyComparatorValue(BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		if ((Object) this instanceof AbstractMinecartEntity minecart) {
			var fabricResult = MinecartComparatorLogicRegistry.getCustomComparatorLogic(minecart.getType());

			if (fabricResult != null) {
				cir.setReturnValue(fabricResult.getComparatorValue(minecart, state, pos));
			}
		}
	}
}
