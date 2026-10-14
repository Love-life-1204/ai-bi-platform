package com.aibi.platform.source.security;

import com.aibi.platform.common.api.ApiErrorCode;
import com.aibi.platform.common.exception.ApiException;
import com.aibi.platform.source.model.DatasourceEndpoint;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TargetSecurityValidator {

    private final boolean allowPrivateNetwork;
    private final Set<Integer> allowedPorts;

    public TargetSecurityValidator(
            @Value("${app.connector.allow-private-network:false}") boolean allowPrivateNetwork,
            @Value("${app.connector.allowed-ports:3306,5432}") Set<Integer> allowedPorts) {
        this.allowPrivateNetwork = allowPrivateNetwork;
        this.allowedPorts = Set.copyOf(allowedPorts);
    }

    public Set<String> validateAndResolve(DatasourceEndpoint endpoint) {
        String host = endpoint.host().trim();
        if (host.contains("://") || host.contains("/") || host.contains("\\") || host.endsWith(".")) {
            blocked("主机名格式不合法");
        }
        if (!allowedPorts.contains(endpoint.port())) {
            blocked("端口不在允许列表中");
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                blocked("DNS 未返回地址");
            }
            for (InetAddress address : addresses) {
                validateAddress(address);
            }
            return Arrays.stream(addresses).map(InetAddress::getHostAddress).collect(Collectors.toUnmodifiableSet());
        } catch (UnknownHostException exception) {
            throw new ApiException(ApiErrorCode.DATASOURCE_CONNECTION_FAILED, "无法解析数据源主机名");
        }
    }

    public void verifyDnsUnchanged(DatasourceEndpoint endpoint, Set<String> approvedAddresses) {
        Set<String> current = validateAndResolve(endpoint);
        if (!approvedAddresses.equals(current)) {
            blocked("连接期间 DNS 解析结果发生变化");
        }
    }

    private void validateAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isMulticastAddress() || isCloudMetadata(address)
                || (!allowPrivateNetwork && isPrivate(address))) {
            blocked("目标解析到保留地址或未批准的内网地址");
        }
    }

    private boolean isPrivate(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            return first == 10 || first == 127 || first == 0
                    || first == 100 && second >= 64 && second <= 127
                    || first == 169 && second == 254
                    || first == 172 && second >= 16 && second <= 31
                    || first == 192 && second == 0 && (third == 0 || third == 2)
                    || first == 192 && second == 168
                    || first == 198 && (second == 18 || second == 19)
                    || first == 198 && second == 51 && third == 100
                    || first == 203 && second == 0 && third == 113
                    || first >= 224;
        }
        if (address instanceof Inet6Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            return (first & 0xfe) == 0xfc || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.getHostAddress().startsWith("2001:db8:");
        }
        return true;
    }

    private boolean isCloudMetadata(InetAddress address) {
        String value = address.getHostAddress();
        return value.equals("169.254.169.254") || value.equals("100.100.100.200")
                || value.equals("fd00:ec2::254");
    }

    private void blocked(String message) {
        throw new ApiException(ApiErrorCode.DATASOURCE_TARGET_BLOCKED, message);
    }
}
