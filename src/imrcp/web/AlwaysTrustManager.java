package imrcp.web;
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;

public class AlwaysTrustManager implements X509TrustManager {

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        // Always trust the client certificates
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        // Always trust the server certificates
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        // Return an empty array to accept all issuers
        return new X509Certificate[0];
    }
} 
