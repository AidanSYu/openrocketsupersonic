package info.openrocket.core.file.openrocket.savers;

import java.util.List;
import java.util.Locale;

import info.openrocket.core.util.MathUtil;

public class FinSetSaver extends ExternalComponentSaver {

	@Override
	protected void addParams(info.openrocket.core.rocketcomponent.RocketComponent c, List<String> elements) {
		super.addParams(c, elements);

		info.openrocket.core.rocketcomponent.FinSet fins = (info.openrocket.core.rocketcomponent.FinSet) c;

		// // this information is already saved as 'RingInstanceable' in RocktComponent
		// elements.add("<fincount>" + fins.getFinCount() + "</fincount>");
		// elements.add("<rotation>" + (fins.getBaseRotation() * 180.0 / Math.PI) +
		// "</rotation>");

		elements.add("<thickness>" + fins.getThickness() + "</thickness>");
		elements.add("<crosssection>" + fins.getCrossSection().name().toLowerCase(Locale.ENGLISH)
				+ "</crosssection>");
		elements.add("<cant>" + Math.toDegrees(fins.getCantAngle()) + "</cant>");

		// Written only when the geometry actually states a leading-edge bevel (i.e. it came
		// from a RASAero import). Fins that never specified one stay NaN and emit nothing, so
		// existing files round-trip byte-for-byte and older readers see no new element.
		if (!Double.isNaN(fins.getLeadingEdgeBevelLength())) {
			elements.add("<leadingedgebevellength>" + fins.getLeadingEdgeBevelLength()
					+ "</leadingedgebevellength>");
		}

		// Save fin tabs only if they exist (compatibility with file version < 1.1)
		if (!MathUtil.equals(fins.getTabHeight(), 0) &&
				!MathUtil.equals(fins.getTabLength(), 0)) {

			elements.add("<tabheight>" + fins.getTabHeight() + "</tabheight>");
			elements.add("<tablength>" + fins.getTabLength() + "</tablength>");
			// TODO: delete this when no backward compatibility with OR 15.03 is needed
			// anymore
			String offset = "center";
			double offsetVal = fins.getTabOffset();
			offset = switch (fins.getTabOffsetMethod()) {
				case TOP -> "front";
				case BOTTOM -> "end";
				case MIDDLE -> "center";
				default -> offset;
			};
			elements.add("<tabposition relativeto=\"" + offset + "\">" +
					offsetVal + "</tabposition>");
			elements.add("<tabposition relativeto=\"" +
					fins.getTabOffsetMethod().name().toLowerCase(Locale.ENGLISH) + "\">" +
					fins.getTabOffset() + "</tabposition>");

		}

		elements.add("<filletradius>" + fins.getFilletRadius() + "</filletradius>");
		elements.add(materialParam("filletmaterial", fins.getFilletMaterial()));
	}

}
