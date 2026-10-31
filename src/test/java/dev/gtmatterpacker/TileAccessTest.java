package dev.gtmatterpacker;

import java.io.IOException;
import java.lang.reflect.Proxy;

import org.junit.Test;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;

public class TileAccessTest {

    @SuppressWarnings("unchecked")
    private <T> T stub(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
    }

    private IGridNode node(boolean allowed) {
        ISecurityGrid security = stub(
            ISecurityGrid.class,
            (p, m, a) -> m.getName()
                .equals("hasPermission") ? allowed : null);
        IGrid grid = stub(
            IGrid.class,
            (p, m, a) -> m.getName()
                .equals("getCache") ? security : null);
        return stub(
            IGridNode.class,
            (p, m, a) -> m.getName()
                .equals("getGrid") ? grid : null);
    }

    @Test(expected = IOException.class)
    public void protectedAETileCannotBePacked() throws Exception {
        IGridNode denied = node(false);
        IGridHost host = stub(
            IGridHost.class,
            (p, m, a) -> m.getName()
                .equals("getGridNode") ? denied : null);
        TileLifecycle.checkAccess(host, null);
    }

    @Test
    public void permittedAETilePassesThePermissionCheck() throws Exception {
        IGridNode allowed = node(true);
        IGridHost host = stub(
            IGridHost.class,
            (p, m, a) -> m.getName()
                .equals("getGridNode") ? allowed : null);
        TileLifecycle.checkAccess(host, null);
    }

    @Test(expected = IOException.class)
    public void cableBusPartSecurityIsAlsoChecked() throws Exception {
        IGridNode denied = node(false);
        IPart part = stub(
            IPart.class,
            (p, m, a) -> m.getName()
                .equals("getGridNode") ? denied : null);
        IPartHost host = stub(
            IPartHost.class,
            (p, m, a) -> m.getName()
                .equals("getPart") ? part : null);
        TileLifecycle.checkAccess(host, null);
    }
}
