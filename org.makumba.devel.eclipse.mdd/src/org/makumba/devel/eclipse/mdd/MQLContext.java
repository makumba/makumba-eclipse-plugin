package org.makumba.devel.eclipse.mdd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Vector;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.resource.IResourceDescription;
import org.eclipse.xtext.resource.IResourceDescriptions;
import org.makumba.devel.eclipse.mdd.MDD.DataDefinition;
import org.makumba.devel.eclipse.mdd.MDD.Declaration;
import org.makumba.devel.eclipse.mdd.MDD.FieldDeclaration;
import org.makumba.devel.eclipse.mdd.MDD.FunctionArgumentBody;
import org.makumba.devel.eclipse.mdd.MDD.FunctionDeclaration;
import org.makumba.devel.eclipse.mdd.MDD.MDDPackage;
import org.makumba.devel.eclipse.mdd.validation.ValidationException;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class MQLContext {

	private IResourceDescriptions resourceDescriptions; //= getInjector().getInstance(IResourceDescriptions.class);;

	protected Iterable<IEObjectDescription> dataDefinitions;

	private LinkedHashMap<String, String> labels;

	private IResource resource;

	private Map<String, FunctionArgumentBody> params = new HashMap<String, FunctionArgumentBody>();

	public static final String[] QueryStringFunctions = { "lower", "upper", "trim", "ltrim", "rtrim", "concat",
			"concat_ws", "substring", "replace", "reverse", "ascii", "character_length", "format", "str_to_date" };
	public static String[] QueryDateFunctions = { "dayOfMonth", "dayOfWeek", "weekday", "week", "dayOfYear", "year",
			"month", "hour", "minute", "second", "extract", "monthName", "dayName", "date_add", "last_day",
			"current_date", "current_time", "current_timestamp", "now" };

	public static final String[] AggregateFunctions = { "AVG", "COUNT", "MAX", "MIN", "SUM" };

	public static final String[] TSFields = { "TS_create", "TS_modify" };

	public MQLContext(LinkedHashMap<String, String> labels, IResourceDescriptions resourceDescriptions, EObject object) {
		this.labels = labels;
		this.resourceDescriptions = resourceDescriptions;
		try {
			this.resource = ResourcesPlugin.getWorkspace().getRoot()
					.getFile(new Path(object.eResource().getURI().toPlatformString(true)));
		} catch (Exception e) {
			this.resource = null;
		}
		setDataDefinitions();

	}

	public MQLContext(LinkedHashMap<String, String> labels, IResourceDescriptions resourceDescriptions,
			IResource currentResource) {
		this.labels = labels;
		this.resourceDescriptions = resourceDescriptions;
		this.resource = currentResource;
		setDataDefinitions();
	}

	public MQLContext(LinkedHashMap<String, String> labels, Iterable<IEObjectDescription> dataDefinitions) {
		this.labels = labels;
		this.dataDefinitions = dataDefinitions;
	}

	public void addLabel(String label, String value) {
		labels.put(label, value);
	}

	public String resolvePath(String path) {
		//first try to see if its a dataDefinition
		if (getDataDefinition(path) != null) {
			//the path already starts with data datfinition, we just return it
			return path;
		} else { //we try to see if it starts with the label
			String label = path.contains(".") ? path.substring(0, path.indexOf(".")) : path;
			return resolveLabel(label) + (path.contains(".") ? path.substring(path.indexOf(".")) : "");
		}
	}

	public String resolveLabel(String label) {
		String path = recursiveLabelResolve(label);
		if (getDataDefinition(path) == null) {
			if (labels.containsKey("this")) {
				return labels.get("this") + "." + path;
			}
		}
		return path;
	}

	private String recursiveLabelResolve(String label) {
		if (labels.containsKey(label)) { // check if label is valid (known)
			String value = labels.get(label); //get the label type
			if (value.indexOf(".") >= 0) { //check if the type starts with another label
				return recursiveLabelResolve(value.substring(0, value.indexOf(".")))
						+ value.substring(value.indexOf(".")); // resolve the new label
			} else { // or maybe it's just a label
				return recursiveLabelResolve(value);
			}
		}
		return label;
	}

	public List<String> getLabelsStartingWith(String startPattern) {
		List<String> result = new ArrayList<String>();
		for (String label : labels.keySet()) {
			if (label.toLowerCase().startsWith(startPattern.toLowerCase())) {
				result.add(label);
			}
		}
		return result;
	}

	public String getValue(String label) {
		return labels.get(label);
	}

	public boolean isValidLabel(String label) {
		String fullPaht = resolveLabel(label);
		return getDataDefinition(fullPaht) != null;
	}

	public boolean containsLabel(String label) {
		return labels.containsKey(label);
	}

	/**
	 * Gets that data definition that is on the beginning of the path. The path
	 * is of syntax:<br>
	 * <br>
	 * 
	 * <code>dataTypeName.feild.field.field</code>
	 * 
	 * @param path
	 * @return
	 */
	public IEObjectDescription getDataDefinition(final String path) {
		try {
			IEObjectDescription ddDescription = Iterables.find(dataDefinitions, new Predicate<IEObjectDescription>() {
				public boolean apply(IEObjectDescription input) {
					return path.startsWith(input.getName());
				}
			});
			return ddDescription;

		} catch (NoSuchElementException e) {

		}
		return null;
	}

	public Iterable<IEObjectDescription> getDataDefinitions() {
		return dataDefinitions;
	}

	public boolean containsParam(String param) {
		return params.containsKey(param);
	}

	public void setParams(Map<String, FunctionArgumentBody> params) {
		this.params = params;
	}

	public FunctionArgumentBody getParam(String param) {
		return params.get(param);
	}

	public boolean isFunctionName(String name) {
		return contains(name, true, QueryDateFunctions, QueryStringFunctions)
				|| contains(name, false, AggregateFunctions);
	}

	private static boolean contains(String element, boolean caseSensitive, String[]... arrays) {
		for (String[] array : arrays) {
			for (String s : array) {
				if (caseSensitive && s.equals(element)) {
					return true;
				} else if (!caseSensitive && s.toLowerCase().equals(element.toLowerCase())) {
					return true;
				}

			}
		}
		return false;
	}

	private void setDataDefinitions() {
		dataDefinitions = Collections.emptySet();
		IProject project = null;
		if (resource != null)
			project = resource.getProject();
		if (project == null || project.isAccessible()) {
			for (IResourceDescription rd : resourceDescriptions.getAllResourceDescriptions()) {
				if (project == null
						|| (rd.getURI().isPlatformResource() && rd.getURI().toPlatformString(true)
								.startsWith(project.getFullPath().toString()))) {
					Iterable<IEObjectDescription> exportedMdds = Iterables.filter(
							rd.getExportedObjects(MDDPackage.Literals.DATA_DEFINITION),
							new Predicate<IEObjectDescription>() {
								public boolean apply(IEObjectDescription input) {
									return input.getEObjectURI().fileExtension().equals("mdd");
								}
							});
					dataDefinitions = Iterables.concat(dataDefinitions, exportedMdds);
				}
			}
		}
	}

	/**
	 * Gets the declarations available for the object that is found at the end
	 * of the path, starting from the inputed {@link Declaration}s. The path is
	 * of syntax <code>fieldName.fieldName.fieldName</code>. If path is empty
	 * the given declarations are returned.
	 * 
	 * @param declarations
	 *            in witch to start the path resolution
	 * @param path
	 *            of the <code>fieldName.fieldName.fieldName</code> syntax
	 * @return the found declarations at the end of the path
	 */
	public Iterable<Declaration> getDeclarationsOnPath(Iterable<Declaration> declarations, String path) {
		if (path == null || path.isEmpty()) //there is no path, so we need return this declarations
			return declarations;

		// we find the name of the first field
		final String fieldName = path.contains(".") ? path.substring(0, path.indexOf(".")) : path;

		Iterable<FieldDeclaration> fields = Iterables.filter(declarations, FieldDeclaration.class);
		try {
			// we search for the field with the given name
			FieldDeclaration field = Iterables.find(fields, new Predicate<FieldDeclaration>() {
				public boolean apply(FieldDeclaration input) {
					return input.getName().equals(fieldName);
				}
			});
			//we repeat the process for the shorter path and the declarations field points to
			String shorterPath = path.contains(".") ? path.substring(path.indexOf(".") + 1) : "";
			return getDeclarationsOnPath(MDDUtils.getPointedDeclarations(field), shorterPath);

		} catch (Exception e) {
			//we didn't find the field, nothing happens
		}
		return Collections.emptySet();
	}

	public EObject resolveQueryIdentifier(String ident, ResourceSet resourceSet, final boolean isFunction)
			throws ValidationException {
		if (!ident.contains(".")) {
			if (isFunction) {
				if (isFunctionName(ident))
					return null;
				if (containsLabel("this")) {
					return resolveQueryIdentifier(resolveLabel("this") + "." + ident, resourceSet, true);
				}
				throw new ValidationException("Unknown function:" + ident);
			} else {
				if (ident.toLowerCase().equals("nil")) {
					return null;
				}
				if (containsParam(ident)) {
					return getParam(ident); //it's a function paramater
				}
				if (containsLabel(ident)) {
					//TODO: somehow store the location of label definition
					return null; //it's a label
				}
				if (containsLabel("this")) {
					return resolveQueryIdentifier(resolveLabel("this") + "." + ident, resourceSet, false);
				}
				throw new ValidationException("Unkonwn value:" + ident);
			}
		} else {
			//			String label = context.resolveLabel(ident.substring(0, ident.indexOf(".")));
			//			if (label.equals(ident.substring(0, ident.indexOf(".")))) {
			//				return "Unknown label:" + label;
			//			}
			//			ident = ident.substring(ident.indexOf("."));

			String path = resolvePath(ident);
			IEObjectDescription ddDescription = getDataDefinition(path);
			//IEObjectDescription current = ddDescription;
			if (ddDescription != null) {
				String passed = path.substring(0, ddDescription.getName().length());
				path = path.substring(ddDescription.getName().length());
				if (!path.isEmpty()) { //we still have some path after data type
					if (path.charAt(0) != '.') {
						throw new ValidationException("Found '" + path + "' where '.' was expected");

					}
					path = path.substring(1);
					EcoreUtil2.resolveAll(resourceSet);
					DataDefinition dd = (DataDefinition) EcoreUtil2.resolve((ddDescription.getEObjectOrProxy()),
							resourceSet);

					Iterable<Declaration> declarations = dd.getD();
					String[] segments = path.split("\\.");
					for (int i = 0; i < segments.length; i++) {//we search all the segments
						final String segment = segments[i];
						if (i < segments.length - 1) { //we are not at the last segment so it has to be a point or set
							try {
								Iterable<FieldDeclaration> fields = Iterables.filter(declarations,
										FieldDeclaration.class);
								FieldDeclaration field = Iterables.find(fields, new Predicate<FieldDeclaration>() {
									public boolean apply(FieldDeclaration input) {
										return input.getName().equals(segment);
									}
								});
								passed += "." + segment;
								declarations = MDDUtils.getPointedDeclarations(field);
							} catch (Exception e) { //in the middle of the path we have a non existing field
								throw new ValidationException("No such field '" + segment + "' in '" + passed);
							}
						} else { //we are at the last segment we check if it is an existing function or field
							try {
								Declaration result = Iterables.find(declarations, new Predicate<Declaration>() {
									public boolean apply(Declaration input) {
										if (!isFunction && (input instanceof FieldDeclaration))
											return ((FieldDeclaration) input).getName().equals(segment);
										if (isFunction && (input instanceof FunctionDeclaration))
											return ((FunctionDeclaration) input).getName().equals(segment);
										return false;
									}
								});
								passed += "." + segment;
								return result;
							} catch (Exception e) { //at the end of the path we have a non existing field
								throw new ValidationException("No such " + (isFunction ? "function" : "field") + " '"
										+ segment + "' in '" + passed);
							}
						}
					}
				}
			} else {
				throw new ValidationException("Unknown Data Type at the begining of: " + path);
			}
		}
		return null;
	}

	public Vector<MQLQuerySegment> resolveFromPath(String path, ResourceSet resourceSet) throws ValidationException {

		Vector<MQLQuerySegment> result = new Vector<MQLQuerySegment>();
		String passed = "";
		path = resolvePath(path);

		IEObjectDescription ddDescription = getDataDefinition(path);
		//IEObjectDescription current = ddDescription;
		if (ddDescription != null) {
			result.add(new MQLQuerySegment(ddDescription.getQualifiedName(), ddDescription.getEObjectOrProxy()));
			passed = path.substring(0, ddDescription.getName().length());
			path = path.substring(ddDescription.getName().length());
			if (!path.isEmpty()) { //we still have some path after data type
				if (path.charAt(0) != '.') {
					throw new ValidationException("Found '" + path + "' where '.' was expected");

				}
				path = path.substring(1);
				EcoreUtil2.resolveAll(resourceSet);
				DataDefinition dd = (DataDefinition) EcoreUtil2.resolve((ddDescription.getEObjectOrProxy()),
						resourceSet);

				Iterable<FieldDeclaration> fields = MDDUtils.getPointerOrSetFields(dd.getD());
				String[] segments = path.split("\\.");
				FieldDeclaration field = null;
				for (final String segment : segments) {
					try {
						field = Iterables.find(fields, new Predicate<FieldDeclaration>() {
							public boolean apply(FieldDeclaration input) {
								return input.getName().equals(segment);
							}
						});
						fields = Iterables.filter(MDDUtils.getPointedDeclarations(field), FieldDeclaration.class);
						passed += "." + segment;
						result.add(new MQLQuerySegment(segment, field));
					} catch (Exception e) {
						throw new ValidationException("No such field '" + segment + "' in '" + passed);
					}
				}
			}
		} else {
			throw new ValidationException("Unknown Data Type at the begining of: " + path);
		}
		return result;
	}
	/**
	 * Gets the declarations available for the object that is found at the end
	 * of the path, starting from the inputed object. The path is of syntax
	 * <code>fieldName.fieldName.fieldName</code>. If path is empty the given
	 * declarations are returned.
	 * 
	 * Test implementation
	 * 
	 * @param object
	 *            from which to start the search
	 * @param path
	 *            of the <code>fieldName.fieldName.fieldName</code> syntax
	 * @return the found declarations at the end of the path
	 */
	/*
	public Iterable<IEObjectDescription> getObjectsOnPath(final IEObjectDescription object, String path) {

		//get the declarations of the object, that will be our first search target
		Iterable<IEObjectDescription> declarations = getDeclarations(object);

		if (path == null || path.isEmpty()) //we don't have path so just return declarations
			return declarations;

		// we find the name of the first field
		final String fieldName = path.contains(".") ? path.substring(0, path.indexOf(".")) : path;

		try {
			//TODO: could be optimized to search all resource declarations at the same time
			//find reference if it points to another data type
			IEObjectDescription field = Iterables.find(declarations, new Predicate<IEObjectDescription>() {
				@Override
				public boolean apply(IEObjectDescription input) {
					if (input.getEClass().equals(MDDPackage.Literals.DECLARATION))
						return input.equals(object.getQualifiedName() + "." + fieldName);
					return false;
				}
			});
			//we repeat the process for the shorter path and the declarations field points to
			String shorterPath = path.contains(".") ? path.substring(path.indexOf(".") + 1) : "";
			return getObjectsOnPath(field, shorterPath);

		} catch (NoSuchElementException e) {
			//we didn't find the field, nothing happens
		}
		return Collections.emptySet();
	}*/

}
