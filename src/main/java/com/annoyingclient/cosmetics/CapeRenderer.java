package com.annoyingclient.cosmetics;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.module.impl.visual.CosmeticsModule;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws player capes thinner than vanilla. It reuses the vanilla cape cube (its size, pose and texture
 * coordinates) and only moves the outer face closer to the player's back.
 */
public final class CapeRenderer {

    /** Thin capes are this fraction of the vanilla 1-pixel depth. */
    public static final float THIN_DEPTH = 0.35f;
    /** Capes drawn by this renderer so far (the game test checks the mixin is active). */
    public static volatile long drawn;

    private CapeRenderer() {
    }

    /** Returns false when the vanilla cape should be drawn instead. */
    public static boolean submit(HumanoidModel<AvatarRenderState> model, AvatarRenderState state, PoseStack poseStack,
                                 SubmitNodeCollector collector, RenderType renderType, int light) {
        CosmeticsModule m = AnnoyingClient.getInstance() == null ? null : AnnoyingClient.modules().get(CosmeticsModule.class);
        if (m == null || !m.thinCapes()) {
            return false;
        }
        model.setupAnim(state);
        ModelPart cape = model.body.getChild("cape");
        ModelPart.Cube[] cube = new ModelPart.Cube[1];
        cape.visit(new PoseStack(), (pose, name, index, c) -> cube[0] = c);
        if (cube[0] == null) {
            return false;
        }
        poseStack.pushPose();
        model.root().translateAndRotate(poseStack);
        model.body.translateAndRotate(poseStack);
        cape.translateAndRotate(poseStack);
        ModelPart.Cube c = cube[0];
        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> emit(pose, consumer, c, light));
        poseStack.popPose();
        drawn++;
        return true;
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer consumer, ModelPart.Cube cube, int light) {
        Matrix4f matrix = pose.pose();
        Vector3f normal = new Vector3f();
        Vector3f pos = new Vector3f();
        for (ModelPart.Polygon polygon : cube.polygons) {
            pose.transformNormal(polygon.normal(), normal);
            for (ModelPart.Vertex v : polygon.vertices()) {
                // maxZ is the side against the player's back; keep it there and pull the outer side in.
                float z = cube.maxZ - (cube.maxZ - v.z()) * THIN_DEPTH;
                matrix.transformPosition(v.x() / 16f, v.y() / 16f, z / 16f, pos);
                consumer.addVertex(pos.x(), pos.y(), pos.z(), -1, v.u(), v.v(), OverlayTexture.NO_OVERLAY, light,
                        normal.x(), normal.y(), normal.z());
            }
        }
    }
}
