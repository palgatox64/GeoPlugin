package me.palgato.geoplugin;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class GeoManager implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(GeoManager.class.getName());
    private static final String UNKNOWN_COUNTRY = "XX";

    private final DatabaseReader reader;

    public GeoManager(File databaseFile) throws IOException {
        if (!databaseFile.exists() || !databaseFile.canRead()) {
            throw new IOException("Database file not accessible: " + databaseFile.getAbsolutePath());
        }
        this.reader = new DatabaseReader.Builder(databaseFile).build();
    }

    public Optional<String> getCountryCode(InetAddress ip) {
        if (ip == null) {
            return Optional.empty();
        }

        try {
            CountryResponse response = reader.country(ip);
            String isoCode = response.getCountry().getIsoCode();
            return Optional.ofNullable(isoCode);
        } catch (IOException | GeoIp2Exception e) {
            LOGGER.log(Level.FINE, "Unable to resolve country for IP: " + ip.getHostAddress(), e);
            return Optional.empty();
        }
    }

    public String getCountryCodeOrDefault(InetAddress ip) {
        return getCountryCode(ip).orElse(UNKNOWN_COUNTRY);
    }

    @Override
    public void close() {
        try {
            reader.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to close GeoIP database reader", e);
        }
    }
}