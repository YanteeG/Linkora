using System.Text.Json;

namespace Linkora.Protocol;

public static class JsonCodec
{
    public static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web)
    {
        PropertyNamingPolicy = null,
        DictionaryKeyPolicy = null,
        WriteIndented = false,
        AllowTrailingCommas = false,
        ReadCommentHandling = JsonCommentHandling.Disallow
    };

    public static string Serialize<T>(T value) => JsonSerializer.Serialize(value, Options);

    public static T? Deserialize<T>(string json) =>
        JsonSerializer.Deserialize<T>(json, Options);

    public static BaseMessage? ParseBase(string json)
    {
        try { return Deserialize<BaseMessage>(json); }
        catch (JsonException) { return null; }
    }
}
