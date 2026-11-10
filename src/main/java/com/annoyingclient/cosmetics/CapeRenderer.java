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
 * Draws player capes thinner and/or waving. It reuses the vanilla cape cube (its size and texture
 * coordinates) and only changes the geometry: the depth is scaled down, and for the wavy style each face
 * is cut into horizontal strips that ripple outwards, away from the player's back.
 */
public final class CapeRenderer {

    /** Thin capes are this fraction of the vanilla 1-pixel depth. */
    public static final float THIN_DEPTH = 0.35f;
    private static final int WAVE_STRIPS = 16;
    /** Capes drawn by this renderer so far (the game test checks the mixin is active). */
    public static volatile long drawn;

    private CapeRenderer() {
    }

    /** Returns false when the vanilla cape should be drawn instead. */
    public static boolean submit(HumanoidModel<AvatarRenderState> model, AvatarRenderState state, PoseStack poseStack,
                                 SubmitNodeCollector collector, RenderType renderType, int light) {
        CosmeticsModule m = AnnoyingClient.getInstance() == null ? null : AnnoyingClient.modules().get(CosmeticsModule.class);
        if (m == null || (!m.thinCapes() && !m.wavyCapes())) {
            return false;
        }
        model.setupAnim(state);
        ModelPart cape = model.body.getChild("cape");
        ModelPart.Cube[] cube = new ModelPart.Cube[1];
        cape.visit(new PoseStack(), (pose, name, index, c) -> cube[0] = c);
        if (cube[0] == null) {
            return false;
        }
        float depth = m.thinCapes() ? THIN_DEPTH : 1f;
        boolean wavy = m.wavyCapes();
        // Faster ripples and a bigger swing while the player moves.
        float speed = Math.min(1f, state.walkAnimationSpeed);
        float amplitude = 1.4f + 1.8f * speed;
        float phase = state.ageInTicks * (0.22f + 0.25f * speed);

        poseStack.pushPose();
        model.root().translateAndRotate(poseStack);
        model.body.translateAndRotate(poseStack);
        cape.translateAndRotate(poseStack);
        ModelPart.Cube c = cube[0];
        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) ->
                emit(pose, consumer, c, depth, wavy, amplitude, phase, light));
        poseStack.popPose();
        drawn++;
        return true;
    }

    /** How far (in pixels) the cape bulges out at height {@code t} (0 = shoulders, 1 = bottom edge). */
    static float wave(float t, float amplitude, float phase) {
        return amplitude * t * (0.5f + 0.5f * (float) Math.sin(t * 7.5f - phase));
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer consumer, ModelPart.Cube cube, float depth, boolean wavy,
                             float amplitude, float phase, int light) {
        Matrix4f matrix = pose.pose();
        Vector3f normal = new Vector3f();
        Vector3f pos = new Vector3f();
        int strips = wavy ? WAVE_STRIPS : 1;
        for (ModelPart.Polygon polygon : cube.polygons) {
            pose.transformNormal(polygon.normal(), normal);
            ModelPart.Vertex[] v = polygon.vertices();
            // Cut the face across the direction in which its height changes.
            boolean alongFirstEdge = Math.abs(v[1].y() - v[0].y()) > Math.abs(v[2].y() - v[1].y());
            for (int s = 0; s < strips; s++) {
                float t0 = s / (float) strips, t1 = (s + 1) / (float) strips;
                for (int corner = 0; corner < 4; corner++) {
                    // Corners in the same winding order as the original quad: (0,0) (1,0) (1,1) (0,1).
                    float a = corner == 1 || corner == 2 ? 1 : 0;
                    float b = corner >= 2 ? 1 : 0;
                    if (alongFirstEdge) {
                        a = a == 0 ? t0 : t1;
                    } else {
                        b = b == 0 ? t0 : t1;
                    }
                    float w0 = (1 - a) * (1 - b), w1 = a * (1 - b), w2 = a * b, w3 = (1 - a) * b;
                    float x = w0 * v[0].x() + w1 * v[1].x() + w2 * v[2].x() + w3 * v[3].x();
                    float y = w0 * v[0].y() + w1 * v[1].y() + w2 * v[2].y() + w3 * v[3].y();
                    float z = w0 * v[0].z() + w1 * v[1].z() + w2 * v[2].z() + w3 * v[3].z();
                    float u = w0 * v[0].u() + w1 * v[1].u() + w2 * v[2].u() + w3 * v[3].u();
                    float vv = w0 * v[0].v() + w1 * v[1].v() + w2 * v[2].v() + w3 * v[3].v();
                    // maxZ is the side against the player's back; keep it there and pull the outer side in.
                    z = cube.maxZ - (cube.maxZ - z) * depth;
                    if (wavy) {
                        z -= wave((y - cube.minY) / (cube.maxY - cube.minY), amplitude, phase);
                    }
                    matrix.transformPosition(x / 16f, y / 16f, z / 16f, pos);
                    consumer.addVertex(pos.x(), pos.y(), pos.z(), -1, u, vv, OverlayTexture.NO_OVERLAY, light,
                            normal.x(), normal.y(), normal.z());
                }
            }
        }
    }
}
