using Xunit;
using System.Net;
using Infinity.WindowsCompanion.Security;

namespace Infinity.WindowsCompanion.Tests;

public sealed class NetworkAddressPolicyTests
{
    [Theory]
    [InlineData("127.0.0.1", true)]
    [InlineData("10.10.0.2", true)]
    [InlineData("172.16.0.5", true)]
    [InlineData("172.31.255.1", true)]
    [InlineData("192.168.1.20", true)]
    [InlineData("100.64.0.10", true)]
    [InlineData("100.127.255.254", true)]
    [InlineData("8.8.8.8", false)]
    [InlineData("172.32.0.1", false)]
    [InlineData("100.128.0.1", false)]
    public void AppliesPrivateAndVpnOnlyBindPolicy(string value, bool expected)
    {
        Assert.Equal(expected, NetworkAddressPolicy.IsAllowedBindAddress(IPAddress.Parse(value)));
    }

    [Theory]
    [InlineData("::1", true)]
    [InlineData("fe80::1", true)]
    [InlineData("fd12:3456::1", true)]
    [InlineData("2001:4860:4860::8888", false)]
    public void AppliesIpv6BindPolicy(string value, bool expected)
    {
        Assert.Equal(expected, NetworkAddressPolicy.IsAllowedBindAddress(IPAddress.Parse(value)));
    }
}
