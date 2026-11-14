package com.samadhanai.samadhanai.Common;

import com.samadhanai.samadhanai.Common.Enums.DepartmentType;

/**
 * Current public contact addresses for the Indore / Madhya Pradesh demo area.
 * Keep this directory in one place so email sending and the dashboard agree.
 */
public final class DepartmentEmailDirectory {

    private DepartmentEmailDirectory() { }

    public static String forDepartment(DepartmentType department) {
        if (department == null) return "commindore@mpurban.gov.in";

        return switch (department) {
            case PWD -> "qc.mppwd@mp.gov.in";
            case MUNICIPAL_CORPORATION, SANITATION -> "commindore@mpurban.gov.in";
            case ELECTRICITY_BOARD -> "indoreccc1912@gmail.com";
            case WATER_SUPPLY -> "cephedind@nic.in";
        };
    }
}
